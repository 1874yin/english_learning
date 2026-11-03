const test = require('node:test');
const assert = require('node:assert/strict');
const { formatDuration, analysisStatus } = require('../../main/resources/static/js/account.js');

test('listening durations keep short sessions visible and show hours without rounding upward', () => {
  assert.equal(formatDuration(0), '0秒');
  assert.equal(formatDuration(0.8), '不足1秒');
  assert.equal(formatDuration(59), '59秒');
  assert.equal(formatDuration(61), '1分1秒');
  assert.equal(formatDuration(23040), '6小时24分');
});

test('an account change renders the new profile even while the previous account request is pending', async () => {
  const { mount } = require('../../main/resources/static/js/account.js');
  const elements = new Map(), handlers = {};
  const element = () => ({ hidden: true, textContent: '', addEventListener() {}, appendChild() {}, replaceChildren() {} });
  const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
  const profile = id => ({ user: { id, username: id }, stats: { totalSeconds: 0, effectiveEpisodes: 0,
    categoryCount: 0, dailyAverageSeconds: 0, naturalDays: 0, activeDays: 0, preferredPeriod: '暂无记录', timeZone: 'Asia/Shanghai' },
    categories: [], records: [], analysis: null, analysisConfigured: false });
  let resolveOld;
  const oldResponse = new Promise(resolve => { resolveOld = resolve; });
  const account = { user: { id: 'alice' }, profile: null, ready: Promise.resolve(), sync: () => oldResponse };
  mount({ document: { getElementById: get, createElement: element }, CadenceUser: account,
    addEventListener: (name, handler) => { handlers[name] = handler; } });
  await Promise.resolve();
  account.user = { id: 'bob' }; account.profile = profile('bob');
  handlers['cadence:user-change']();
  resolveOld(profile('alice'));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(get('userAvatar').textContent, 'B');
  assert.equal(get('profileLayout').hidden, false);
});

test('saved favorites appear on the profile and can be removed', async () => {
  const { mount } = require('../../main/resources/static/js/account.js');
  const elements = new Map(), handlers = {};
  const element = () => ({ hidden: true, textContent: '', children: [], className: '',
    addEventListener(name, handler) { (this.handlers || (this.handlers = {}))[name] = handler; },
    appendChild(child) { this.children.push(child); },
    replaceChildren(...children) { this.children = children; } });
  const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
  const profile = { user: { id: 'alice', username: 'alice' }, stats: { totalSeconds: 0, effectiveEpisodes: 0,
    categoryCount: 0, dailyAverageSeconds: 0, naturalDays: 0, activeDays: 0, preferredPeriod: '暂无记录', timeZone: 'Asia/Shanghai' },
    categories: [], records: [], analysis: null, analysisConfigured: false,
    favorites: [{ lessonId: 'lesson1', title: 'Morning Coffee', category: 'Daily', duration: 178, available: true,
      favoritedAt: '2026-10-27T10:00:00Z' }] };
  const calls = [];
  const account = { user: profile.user, profile, ready: Promise.resolve(),
    request: async (url, options) => { calls.push({ url, options }); } };
  mount({ document: { getElementById: get, createElement: element }, CadenceUser: account,
    addEventListener: (name, handler) => { handlers[name] = handler; } });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(get('userFavoriteCount').textContent, '共 1 期');
  const row = get('userFavoriteRows').children[0];
  assert.equal(row.children[0].textContent, 'Morning Coffee');
  assert.equal(row.children[2].children[0].href, '/?lesson=lesson1&from=profile');
  const remove = row.children[2].children[1];
  assert.equal(remove.textContent, '取消收藏');
  remove.handlers.click();
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(calls, [{ url: '/api/user/favorites/lesson1', options: { method: 'DELETE' } }]);
  assert.equal(get('userFavoriteCount').textContent, '共 0 期');
  assert.match(get('userFavoriteRows').children[0].children[0].textContent, /还没有收藏/);
});

test('analysis state distinguishes zero, insufficient, eligible and unconfigured accounts', () => {
  assert.match(analysisStatus(0, true), /10/);
  assert.match(analysisStatus(7, true), /3/);
  assert.match(analysisStatus(10, true), /满足/);
  assert.match(analysisStatus(12, false), /未启用/);
});
