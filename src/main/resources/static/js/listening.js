(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.CadenceListening = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  class PlaybackClock {
    constructor() { this.previous = null; }
    reset() { this.previous = null; }
    sample(current) {
      const previous = this.previous;
      this.previous = { ...current };
      if (!previous || !previous.playing || previous.seeking || current.seeking || !previous.ready || !current.ready
          || previous.rate !== current.rate) return 0;
      const elapsed = (current.now - previous.now) / 1000;
      const advanced = current.position - previous.position;
      if (!Number.isFinite(elapsed) || elapsed <= 0 || elapsed > 30 || !Number.isFinite(advanced)
          || advanced <= 0 || current.rate <= 0) return 0;
      const expected = elapsed * current.rate;
      if (Math.abs(advanced - expected) > Math.max(0.5, expected * 0.2)) return 0;
      return Math.min(elapsed, advanced / current.rate);
    }
  }

  class Recorder {
    constructor({ request }) {
      this.request = request;
      this.clock = new PlaybackClock();
      this.sessions = new Set();
      this.current = null;
      this.flushing = null;
    }
    start(lessonId) {
      if (this.current && this.current.lessonId === lessonId) return;
      this.clock.reset();
      this.current = { lessonId, total: 0, position: 0, sent: 0, sentPosition: null,
        sequence: 0, pending: null, streamId: null, starting: null, error: null };
      this.sessions.add(this.current);
      this.ensureStream(this.current);
    }
    ensureStream(session) {
      if (session.streamId) return Promise.resolve(session.streamId);
      if (!session.starting) {
        session.starting = this.request('/api/user/listening/start', { method: 'POST', body: { lessonId: session.lessonId } })
          .then(data => { session.streamId = data.streamId; session.error = null; return data.streamId; })
          .catch(error => { session.error = error; return null; })
          .finally(() => { session.starting = null; });
      }
      return session.starting;
    }
    sample(current) {
      if (!this.current) return;
      this.current.total += this.clock.sample(current);
      this.current.position = Math.max(0, Number(current.position) || 0);
    }
    resetClock() { this.clock.reset(); }
    reset() { this.current = null; this.sessions.clear(); this.clock.reset(); }
    async prepareForAccountChange() {
      await this.flush();
      while ([...this.sessions].some(session => session.pending || session.total !== session.sent || session.position !== session.sentPosition)) {
        await this.flush();
      }
      this.reset();
    }
    flush(options = {}) {
      if (this.flushing) return this.flushing;
      this.flushing = this.send(options).finally(() => { this.flushing = null; });
      return this.flushing;
    }
    async send(options) {
      for (const session of this.sessions) {
        const streamId = await this.ensureStream(session);
        if (!streamId) throw session.error;
        if (!session.pending && (session.total !== session.sent || session.position !== session.sentPosition)) {
          session.pending = { streamId, sequence: session.sequence + 1, seconds: session.total, position: session.position };
        }
        if (session.pending) {
          const report = session.pending;
          await this.request('/api/user/listening', { method: 'POST', body: report, keepalive: Boolean(options.keepalive) });
          session.sequence = report.sequence; session.sent = report.seconds; session.sentPosition = report.position;
          session.pending = null;
        }
        if (session !== this.current && session.total === session.sent) this.sessions.delete(session);
      }
    }
  }

  function storageKeys(userId) {
    const suffix = userId ? '.' + userId : '';
    return { progress: 'cadence.progress.v1' + suffix, history: 'cadence.history.v1' + suffix,
      lastLesson: 'cadence.last-lesson.v1' + suffix };
  }

  function buildImportRecords(progress, history) {
    const values = new Map();
    Object.keys(progress || {}).forEach(lessonId => {
      const record = progress[lessonId];
      // Position zero means nothing to resume: the lesson was merely opened,
      // so it must not become a history record. History entries below still
      // import with position 0 because clicking play counts as history.
      if (!record || !Number.isFinite(record.position) || record.position <= 0) return;
      values.set(lessonId, { lessonId, position: record.position,
        updatedAt: Number.isFinite(record.updatedAt) && record.updatedAt >= 0 ? record.updatedAt : 0 });
    });
    (Array.isArray(history) ? history : []).forEach(lessonId => {
      if (typeof lessonId === 'string' && lessonId && !values.has(lessonId)) values.set(lessonId, { lessonId, position: 0, updatedAt: 0 });
    });
    return [...values.values()];
  }

  function mergeProgress(local, records) {
    const merged = Object.assign(Object.create(null), local || {});
    (records || []).forEach(record => {
      const old = merged[record.lessonId];
      if (old && Number(old.updatedAt) > Number(record.updatedAt)) return;
      merged[record.lessonId] = { position: record.position, duration: record.duration,
        updatedAt: record.updatedAt, completed: record.duration > 0 && record.position >= record.duration - 0.8 };
    });
    return merged;
  }

  return { PlaybackClock, Recorder, storageKeys, buildImportRecords, mergeProgress };
});
