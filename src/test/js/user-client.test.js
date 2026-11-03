const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const listening = require('../../main/resources/static/js/listening.js');

function loadClient(user, initial = {}) {
  const data = new Map(Object.entries(initial));
  const calls = [], events = [];
  const document = { readyState: 'loading', addEventListener() {}, querySelectorAll() { return []; } };
  const window = { CadenceListening: listening, localStorage: {
    getItem: k => data.get(k) || null, setItem: (k, v) => data.set(k, v), removeItem: k => data.delete(k)
  }, dispatchEvent: event => events.push(event), addEventListener() {} };
  let current = user, token = current ? 'account-token' : 'guest-token', profileRecords = [];
  const fetch = async (url, options) => {
    calls.push({ url, options });
    let body = null;
    if (url === '/api/user/session') body = { user: current, csrfToken: token };
    if (url === '/api/user/login') {
      const input = JSON.parse(options.body);
      if (input.password === 'wrong') return { ok: false, status: 401, json: async () => ({ message: '用户名或密码不正确。' }) };
      current = { id: input.username, username: input.username };
      token = 'new-token'; body = { user: current, csrfToken: token };
    }
    if (url === '/api/user/profile') body = { user: current, records: profileRecords, stats: { totalSeconds: 0 } };
    return { ok: true, status: body ? 200 : 204, json: async () => body };
  };
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/user.js', 'utf8'), {
    window, document, fetch, AbortController, setTimeout, clearTimeout, console,
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } }
  });
  return { client: window.CadenceUser, calls, events, data,
    switchServerAccount(value, records) { current = value; token = 'server-changed-token'; profileRecords = records; } };
}

test('cross-tab account changes never save another account profile into the old cache', async () => {
  const env = loadClient({ id: 'alice', username: 'Alice' });
  await env.client.ready;
  env.switchServerAccount({ id: 'bob', username: 'Bob' }, [
    { lessonId: 'bob-private-lesson', position: 99, duration: 100, updatedAt: 500, available: true }
  ]);
  await assert.rejects(env.client.sync(), /账号|登录状态/);
  assert.equal(env.client.user.id, 'bob');
  assert.equal(env.data.get('cadence.progress.v1.alice').includes('bob-private-lesson'), false);
});

test('authenticated bootstrap migrates guest positions once and removes only successfully imported guest data', async () => {
  const env = loadClient({ id: 'alice', username: 'Alice' }, {
    'cadence.progress.v1': JSON.stringify({ lesson: { position: 15, updatedAt: 100 } }),
    'cadence.history.v1': JSON.stringify(['lesson'])
  });
  await env.client.ready;
  const imported = env.calls.find(c => c.url === '/api/user/import');
  assert.equal(imported.options.headers['X-CSRF-Token'], 'account-token');
  assert.equal(JSON.parse(imported.options.body).records[0].position, 15);
  assert.equal(env.data.has('cadence.progress.v1'), false);
  await env.client.sync();
  assert.equal(env.calls.filter(c => c.url === '/api/user/import').length, 1);
});

test('login waits for old-account flush hooks and uses the new token for the new account', async () => {
  const env = loadClient({ id: 'alice', username: 'Alice' });
  await env.client.ready;
  let flushed = false;
  env.client.beforeChange(async () => { await Promise.resolve(); flushed = true; });
  await env.client.login({ username: 'bob', password: 'correct-password' });
  assert.equal(flushed, true);
  assert.equal(env.client.user.id, 'bob');
  await env.client.request('/api/user/analysis', { method: 'POST' });
  assert.equal(env.calls.at(-1).options.headers['X-CSRF-Token'], 'new-token');
  assert.equal(env.data.has('cadence.progress.v1.bob'), true);
});

test('wrong login credentials report the error without clearing a previous account', async () => {
  const env = loadClient({ id: 'alice', username: 'Alice' });
  await env.client.ready;
  await assert.rejects(env.client.login({ username: 'bob', password: 'wrong' }), /用户名或密码不正确/);
  assert.equal(env.client.user.id, 'alice');
});
