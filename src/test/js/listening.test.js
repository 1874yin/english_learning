const test = require('node:test');
const assert = require('node:assert/strict');
const { PlaybackClock, Recorder, buildImportRecords, mergeProgress, storageKeys } = require('../../main/resources/static/js/listening.js');

const sample = (now, position, extra = {}) => ({ now, position, rate: 1, playing: true, seeking: false, ready: true, ...extra });

test('actual wall time is counted at normal and accelerated playback speeds', () => {
  const clock = new PlaybackClock();
  assert.equal(clock.sample(sample(0, 0)), 0);
  assert.equal(clock.sample(sample(1000, 1)), 1);
  assert.equal(clock.sample(sample(2000, 3, { rate: 2 })), 0);
  assert.equal(clock.sample(sample(3000, 5, { rate: 2 })), 1);
});

test('pause, buffering and seek jumps never fabricate listening time', () => {
  const clock = new PlaybackClock();
  clock.sample(sample(0, 0));
  assert.equal(clock.sample(sample(1000, 80)), 0);
  assert.equal(clock.sample(sample(2000, 80, { playing: false })), 0);
  assert.equal(clock.sample(sample(3000, 80, { playing: false })), 0);
  clock.sample(sample(4000, 80));
  assert.equal(clock.sample(sample(5000, 80, { ready: false })), 0);
  assert.equal(clock.sample(sample(6000, 90, { seeking: true })), 0);
  assert.equal(clock.sample(sample(7000, 91)), 0);
  assert.equal(clock.sample(sample(8000, 92)), 1);
});

test('replaying a segment counts real time but a backwards jump does not', () => {
  const clock = new PlaybackClock();
  clock.sample(sample(0, 20));
  assert.equal(clock.sample(sample(1000, 21)), 1);
  assert.equal(clock.sample(sample(2000, 10)), 0);
  assert.equal(clock.sample(sample(3000, 11)), 1);
});

test('unmeasurable long browser suspension is not counted as listening', () => {
  const clock = new PlaybackClock();
  clock.sample(sample(0, 0));
  assert.equal(clock.sample(sample(600000, 600)), 0);
});

test('guest imports contain only positions and timestamps, never claimed listening time', () => {
  const records = buildImportRecords({
    a: { position: 90, updatedAt: 1000, listenedSeconds: 999, completed: true },
    opened: { position: 0, updatedAt: 500 },
    bad: { position: -1 }
  }, ['a', 'b', 'opened']);
  assert.deepEqual(records, [
    { lessonId: 'a', position: 90, updatedAt: 1000 },
    { lessonId: 'b', position: 0, updatedAt: 0 },
    { lessonId: 'opened', position: 0, updatedAt: 0 }
  ]);
});

test('server and per-account cached progress merge by update time without sharing accounts', () => {
  const merged = mergeProgress({ a: { position: 40, updatedAt: 4000 } }, [
    { lessonId: 'a', position: 20, duration: 100, updatedAt: 2000 },
    { lessonId: 'b', position: 30, duration: 100, updatedAt: 3000 }
  ]);
  assert.equal(merged.a.position, 40);
  assert.equal(merged.b.position, 30);
  assert.equal(merged.b.completed, false);
  assert.notEqual(storageKeys('alice').progress, storageKeys('bob').progress);
  assert.equal(storageKeys(null).progress, 'cadence.progress.v1');
});

test('failed reports retry the identical sequence and cumulative value before sending new samples', async () => {
  const reports = [];
  let fail = true;
  const recorder = new Recorder({ request: async (url, options) => {
    if (url.endsWith('/start')) return { streamId: 'stream-1' };
    reports.push({ ...options.body });
    if (fail) { fail = false; throw new Error('offline'); }
  }});
  recorder.start('lesson1');
  recorder.sample(sample(0, 0)); recorder.sample(sample(1000, 1));
  await assert.rejects(recorder.flush(), /offline/);
  recorder.sample(sample(2000, 2));
  await recorder.flush();
  await recorder.flush();
  assert.equal(reports[0].sequence, reports[1].sequence);
  assert.equal(reports[0].seconds, reports[1].seconds);
  assert.equal(reports[2].sequence, 2);
  assert.equal(reports[2].seconds, 2);
});

test('account changes preserve unsent samples on failure and drain retries before resetting', async () => {
  let offline = true;
  const reports = [];
  const recorder = new Recorder({ request: async (url, options) => {
    if (url.endsWith('/start')) return { streamId: 'original-account-stream' };
    reports.push({ ...options.body });
    if (offline) throw new Error('offline');
  }});
  recorder.start('lesson1'); recorder.sample(sample(0, 0)); recorder.sample(sample(1000, 1));
  await assert.rejects(recorder.prepareForAccountChange(), /offline/);
  assert.equal(recorder.current.total, 1);
  recorder.sample(sample(2000, 2)); offline = false;
  await recorder.prepareForAccountChange();
  assert.equal(recorder.current, null);
  assert.deepEqual(reports.map(report => report.seconds), [1, 1, 2]);
});

test('switching lessons flushes each session to its own stream', async () => {
  const reports = [];
  const recorder = new Recorder({ request: async (url, options) => {
    if (url.endsWith('/start')) return { streamId: options.body.lessonId + '-stream' };
    reports.push({ ...options.body });
  }});
  recorder.start('one'); recorder.sample(sample(0, 0)); recorder.sample(sample(1000, 1));
  recorder.start('two'); recorder.sample(sample(2000, 0)); recorder.sample(sample(3000, 1));
  await recorder.flush();
  assert.deepEqual(reports.map(r => [r.streamId, r.seconds]), [['one-stream', 1], ['two-stream', 1]]);
});
