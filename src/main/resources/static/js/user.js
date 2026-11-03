(() => {
  'use strict';
  const helpers = window.CadenceListening;
  const hooks = [];
  let user = null, csrf = '', profile = null, syncError = '', syncedUserId = null;
  let dialog = null, mode = 'login', busy = false, opener = null, refreshingSession = null;
  const SESSION_NOTICE_KEY = 'cadence.session-change.v1';

  function read(key, fallback) {
    try { const raw = window.localStorage.getItem(key); return raw ? JSON.parse(raw) : fallback; }
    catch (_) { return fallback; }
  }
  function write(key, value) {
    try { window.localStorage.setItem(key, JSON.stringify(value)); }
    catch (_) { /* Server sync remains available when browser storage is full. */ }
  }
  function updateHeader() {
    document.querySelectorAll('[data-auth-button]').forEach(el => { el.hidden = Boolean(user); });
    document.querySelectorAll('[data-user-home], [data-user-logout]').forEach(el => { el.hidden = !user; });
    document.querySelectorAll('[data-user-name]').forEach(el => { el.textContent = user ? user.username : ''; });
    document.querySelectorAll('[data-user-message]').forEach(el => { el.textContent = syncError; el.hidden = !syncError; });
    document.querySelectorAll('.history-note').forEach(el => {
      el.textContent = user ? '已登录：收听记录与进度将同步到账号，完整统计可在用户主页查看。' : '游客记录仅保存在当前浏览器；注册或登录后可同步记录并生成学习画像。';
    });
  }
  function emit() {
    updateHeader();
    window.dispatchEvent(new CustomEvent('cadence:user-change', { detail: { user, profile, syncError } }));
  }
  async function request(url, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeout || 15000);
    const headers = { Accept: 'application/json' };
    if (user && !['/api/user/session', '/api/user/login', '/api/user/register'].includes(url)) headers['X-User-Id'] = user.id;
    const method = options.method || 'GET';
    if (method !== 'GET') headers['X-CSRF-Token'] = csrf;
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    try {
      const response = await fetch(url, { method, headers, credentials: 'same-origin', cache: 'no-store',
        signal: controller.signal, keepalive: Boolean(options.keepalive),
        body: options.body === undefined ? undefined : JSON.stringify(options.body) });
      let body = null;
      if (response.status !== 204) {
        try { body = await response.json(); } catch (_) { body = null; }
      }
      if (!response.ok) {
        if (response.status === 401 && !['/api/user/login', '/api/user/register'].includes(url) && user) {
          user = null; profile = null; csrf = ''; syncedUserId = null; syncError = '登录已过期，请重新登录；本机进度仍然保留。'; emit();
        }
        if (response.status === 403 || (body && body.code === 'ACCOUNT_CHANGED')) {
          await refreshSession('登录状态已更新，请重新操作；本机进度仍然保留。');
        }
        const error = new Error(body && body.message ? body.message : '请求失败，请稍后重试。');
        error.status = response.status; throw error;
      }
      return body;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('请求超时，请稍后重试。');
      throw error;
    } finally { clearTimeout(timeout); }
  }
  function refreshSession(message) {
    if (refreshingSession) return refreshingSession;
    refreshingSession = (async () => {
      const session = await request('/api/user/session');
      user = session.user || null; csrf = session.csrfToken; profile = null; syncedUserId = null;
      syncError = message; emit();
      return user;
    })().finally(() => { refreshingSession = null; });
    return refreshingSession;
  }
  function announceSessionChange() {
    write(SESSION_NOTICE_KEY, { at: Date.now(), nonce: Math.random() });
  }
  async function sync() {
    if (!user) return null;
    const id = user.id;
    const keys = helpers.storageKeys(id), guestKeys = helpers.storageKeys(null);
    if (syncedUserId !== id) {
      const cached = helpers.buildImportRecords(read(keys.progress, {}), read(keys.history, []));
      const guest = helpers.buildImportRecords(read(guestKeys.progress, {}), read(guestKeys.history, []));
      const imports = cached.concat(guest);
      let guestProgressRaw, guestHistoryRaw;
      try { guestProgressRaw = window.localStorage.getItem(guestKeys.progress); guestHistoryRaw = window.localStorage.getItem(guestKeys.history); } catch (_) { /* Storage may be unavailable. */ }
      for (let offset = 0; offset < imports.length; offset += 1000) {
        await request('/api/user/import', { method: 'POST', body: { records: imports.slice(offset, offset + 1000) } });
      }
      if (!user || user.id !== id) return null;
      if (guest.length) {
        try {
          if (window.localStorage.getItem(guestKeys.progress) === guestProgressRaw) window.localStorage.removeItem(guestKeys.progress);
          if (window.localStorage.getItem(guestKeys.history) === guestHistoryRaw) window.localStorage.removeItem(guestKeys.history);
        } catch (_) { /* Imported data is already safely stored on the server. */ }
      }
      syncedUserId = id;
    }
    const fresh = await request('/api/user/profile');
    if (!user || user.id !== id) return null;
    if (!fresh || !fresh.user || fresh.user.id !== id) {
      await refreshSession('登录账号已在其他标签页变化，请重新操作。');
      throw new Error('登录账号已变化，未保存不属于当前账号的记录。');
    }
    profile = fresh;
    write(keys.progress, helpers.mergeProgress(read(keys.progress, {}), fresh.records));
    write(keys.history, fresh.records.filter(record => record.available).map(record => record.lessonId).slice(0, 50));
    syncError = ''; updateHeader();
    return fresh;
  }
  async function bootstrap() {
    try {
      const session = await request('/api/user/session');
      user = session.user || null; csrf = session.csrfToken;
      if (user) await sync();
    } catch (_) { syncError = user ? '本机记录同步失败，进度仍在当前浏览器保留，请刷新重试。' : '账号服务暂时不可用，仍可作为游客收听。'; }
    emit(); return user;
  }
  async function signIn(action, credentials) {
    await api.ready;
    if (!csrf) {
      const session = await request('/api/user/session'); csrf = session.csrfToken;
    }
    for (const hook of hooks) await hook();
    const session = await request('/api/user/' + action, { method: 'POST', body: credentials });
    user = session.user; csrf = session.csrfToken; profile = null; syncError = ''; syncedUserId = null;
    try { await sync(); } catch (_) { syncError = '已登录，但本机记录暂未同步成功；请刷新重试。'; }
    emit(); announceSessionChange(); return session;
  }
  async function logout() {
    for (const hook of hooks) await hook();
    await request('/api/user/logout', { method: 'POST' });
    user = null; profile = null; csrf = ''; syncedUserId = null; syncError = '';
    emit(); announceSessionChange(); await bootstrap();
  }
  function setMode(value) {
    mode = value;
    const register = mode === 'register';
    dialog.querySelector('#authTitle').textContent = register ? '开始积累你的进步' : '欢迎回来，继续聆听';
    dialog.querySelector('#authRegisterTab').setAttribute('aria-selected', String(register));
    dialog.querySelector('#authLoginTab').setAttribute('aria-selected', String(!register));
    dialog.querySelector('#authBenefit').hidden = !register;
    dialog.querySelector('#authSubmit').textContent = register ? '注册并开始记录' : '登录并同步记录';
    dialog.querySelector('#authPassword').autocomplete = register ? 'new-password' : 'current-password';
    dialog.querySelector('#authError').hidden = true;
  }
  function open(value = 'login') {
    if (!dialog) initUi();
    opener = document.activeElement;
    setMode(value); dialog.querySelector('form').reset(); dialog.showModal();
    dialog.querySelector('#authUsername').focus();
  }
  function close() {
    if (busy) return;
    dialog.close(); dialog.querySelector('form').reset();
    if (opener) opener.focus();
  }
  function initUi() {
    if (dialog) return;
    dialog = document.createElement('dialog'); dialog.className = 'user-auth-dialog'; dialog.id = 'userAuthDialog';
    dialog.setAttribute('aria-labelledby', 'authTitle');
    dialog.innerHTML = '<div class="user-auth-heading"><h2 id="authTitle">欢迎回来，继续聆听</h2><button type="button" class="user-auth-close" aria-label="关闭登录窗口">×</button></div>'
      + '<div class="user-auth-tabs" role="tablist" aria-label="账号操作"><button type="button" id="authRegisterTab" role="tab" aria-selected="false">注册账号</button><button type="button" id="authLoginTab" role="tab" aria-selected="true">已有账号，登录</button></div>'
      + '<div class="user-auth-benefit" id="authBenefit" hidden><strong>让每一次聆听，都留下足迹。</strong>注册后即可跨设备同步观看 / 收听记录与进度；累计有效收听满 10 期后，可生成专属的兴趣偏好与学习习惯分析。</div>'
      + '<form id="userAuthForm"><label class="user-auth-field" for="authUsername">用户名<input id="authUsername" name="username" autocomplete="username" placeholder="3–30 位文字、数字、下划线等" minlength="3" maxlength="30" required></label>'
      + '<label class="user-auth-field" for="authPassword">密码<input id="authPassword" name="password" type="password" autocomplete="current-password" placeholder="至少 8 位，最多 72 个 UTF-8 字节" minlength="8" maxlength="72" required></label>'
      + '<p class="user-auth-error" id="authError" role="alert" hidden></p><button type="submit" class="user-primary user-auth-submit" id="authSubmit">登录并同步记录</button></form>'
      + '<p class="user-auth-footer">仅需用户名和密码，无须手机号或邮箱。<br>每期实际累计收听达到总时长的 80%，计为有效一期。</p>';
    document.body.appendChild(dialog);
    dialog.querySelector('.user-auth-close').addEventListener('click', close);
    dialog.querySelector('#authRegisterTab').addEventListener('click', () => { if (!busy) setMode('register'); });
    dialog.querySelector('#authLoginTab').addEventListener('click', () => { if (!busy) setMode('login'); });
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    dialog.addEventListener('click', event => {
      if (event.target !== dialog) return;
      const rect = dialog.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close();
    });
    dialog.addEventListener('close', () => { dialog.querySelector('form').reset(); });
    dialog.querySelector('form').addEventListener('submit', async event => {
      event.preventDefault(); if (busy) return;
      const button = dialog.querySelector('#authSubmit'), error = dialog.querySelector('#authError');
      busy = true; button.disabled = true; button.textContent = '正在处理…'; error.hidden = true;
      try {
        await signIn(mode, { username: dialog.querySelector('#authUsername').value.trim(), password: dialog.querySelector('#authPassword').value });
        busy = false; close();
      } catch (failure) {
        error.textContent = failure.message || '账号服务暂时不可用，请稍后重试。'; error.hidden = false;
      } finally { busy = false; button.disabled = false; button.textContent = mode === 'register' ? '注册并开始记录' : '登录并同步记录'; }
    });
    document.addEventListener('click', async event => {
      const target = event.target.closest('[data-auth-button], [data-user-logout]');
      if (!target) return;
      if (target.hasAttribute('data-auth-button')) { open(target.dataset.authButton || 'login'); return; }
      target.disabled = true;
      try { await logout(); }
      catch (_) { syncError = '退出未成功，请检查网络后重试。'; updateHeader(); }
      finally { target.disabled = false; }
    });
    updateHeader();
  }
  const api = { request, sync, login: credentials => signIn('login', credentials),
    register: credentials => signIn('register', credentials), logout, open,
    beforeChange: hook => hooks.push(hook), get user() { return user; }, get profile() { return profile; },
    get syncError() { return syncError; }, read, write };
  window.CadenceUser = api;
  window.addEventListener('pageshow', event => {
    if (event.persisted) refreshSession('已重新检查登录状态。').catch(() => {
      syncError = '请刷新页面以确认当前登录状态。'; updateHeader();
    });
  });
  window.addEventListener('storage', event => {
    if (event.key === SESSION_NOTICE_KEY) refreshSession('登录状态已在其他标签页更新。').catch(() => {
      syncError = '登录状态可能已变化，请刷新页面后继续。'; updateHeader();
    });
  });
  api.ready = bootstrap();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initUi);
  else initUi();
})();
