(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else api.mount(root);
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';
  function formatDuration(value) {
    const seconds = Math.max(0, Number(value) || 0);
    if (seconds > 0 && seconds < 1) return '不足1秒';
    const whole = Math.floor(seconds), minutes = Math.floor(whole / 60), hours = Math.floor(minutes / 60);
    if (hours) return hours + '小时' + (minutes % 60 ? minutes % 60 + '分' : '');
    if (minutes) return minutes + '分' + (whole % 60 ? whole % 60 + '秒' : '');
    return whole + '秒';
  }
  function analysisStatus(episodes, configured) {
    if (episodes < 10) return '再听满 ' + (10 - episodes) + ' 期即可分析';
    return configured ? '已满足 10 期要求' : '分析服务暂未启用';
  }
  function mount(window) {
    const document = window.document, account = window.CadenceUser;
    const $ = id => document.getElementById(id);
    let current = null, visibleRecords = 20, loadRequestId = 0, generating = false;
    const node = (tag, className, text) => {
      const value = document.createElement(tag);
      if (className) value.className = className;
      if (text !== undefined) value.textContent = text;
      return value;
    };
    function date(value, withTime = true) {
      if (!value) return '暂无记录';
      return new Intl.DateTimeFormat('zh-CN', { timeZone: current.stats.timeZone,
        year: 'numeric', month: '2-digit', day: '2-digit', ...(withTime ? { hour: '2-digit', minute: '2-digit', hour12: false } : {}) }).format(new Date(value));
    }
    function renderAnalysis(analysis) {
      $('analysisResult').hidden = !analysis;
      $('generateAnalysis').textContent = analysis ? '重新生成学习画像' : '生成我的学习画像';
      if (!analysis) return;
      $('analysisTitle').textContent = analysis.title;
      $('analysisInterests').textContent = analysis.interests;
      $('analysisHabits').textContent = analysis.habits;
      $('analysisInvestment').textContent = analysis.investment;
      $('analysisEncouragement').textContent = analysis.encouragement;
      $('analysisMeta').textContent = date(analysis.generatedAt) + ' · 基于 ' + analysis.basedOnEpisodes + ' 期有效收听';
      $('analysisSuggestions').replaceChildren(...analysis.suggestions.map(text => node('li', '', text)));
    }
    function renderRecords() {
      $('userRecordCount').textContent = '共 ' + current.records.length + ' 期';
      const rows = current.records.slice(0, visibleRecords).map(record => {
        const row = node('tr');
        const title = node('td', 'user-record-title', record.title);
        const status = record.effective ? '已达有效收听标准' : record.imported ? '旧本机记录 · 历史时长未知' : '继续收听以积累有效期数';
        title.appendChild(node('span', 'user-record-sub', (record.category || '未分类') + ' · ' + status));
        row.appendChild(title);
        row.appendChild(node('td', 'user-record-date', record.lastListenedAt ? date(record.lastListenedAt) : '旧记录 · 日期未知'));
        row.appendChild(node('td', 'user-number', formatDuration(record.listenedSeconds)));
        const action = node('td', 'user-number');
        if (record.available) {
          const link = node('a', 'user-record-link', record.position > 0 && record.position < record.duration - 1 ? '继续收听' : '重新收听');
          link.href = '/?' + new URLSearchParams({ lesson: record.lessonId, from: 'profile' }).toString();
          action.appendChild(link);
        } else action.appendChild(node('span', 'user-small', '课程已下架'));
        row.appendChild(action);
        return row;
      });
      if (!rows.length) { const row = node('tr'), cell = node('td', 'user-empty', '还没有收听记录，去找一期感兴趣的内容吧。'); cell.colSpan = 4; row.appendChild(cell); rows.push(row); }
      $('userRecordRows').replaceChildren(...rows);
      $('moreUserRecords').hidden = visibleRecords >= current.records.length;
    }
    function renderFavorites() {
      const favorites = Array.isArray(current.favorites) ? current.favorites : [];
      $('userFavoriteCount').textContent = '共 ' + favorites.length + ' 期';
      const rows = favorites.map(favorite => {
        const row = node('tr');
        const title = node('td', 'user-record-title', favorite.title || '课程已下架');
        title.appendChild(node('span', 'user-record-sub', favorite.available
          ? (favorite.category || '未分类') + ' · ' + formatDuration(favorite.duration)
          : '课程已下架'));
        row.appendChild(title);
        row.appendChild(node('td', 'user-record-date', date(favorite.favoritedAt)));
        const action = node('td', 'user-number');
        if (favorite.available) {
          const link = node('a', 'user-record-link', '播放');
          link.href = '/?' + new URLSearchParams({ lesson: favorite.lessonId, from: 'profile' }).toString();
          action.appendChild(link);
        }
        const remove = node('button', 'user-favorite-remove', '取消收藏');
        remove.type = 'button';
        remove.addEventListener('click', () => removeFavorite(favorite.lessonId, remove));
        action.appendChild(remove);
        row.appendChild(action);
        return row;
      });
      if (!rows.length) {
        const row = node('tr'), cell = node('td', 'user-empty', '还没有收藏音频，在播放条点击爱心即可收藏。');
        cell.colSpan = 3; row.appendChild(cell); rows.push(row);
      }
      $('userFavoriteRows').replaceChildren(...rows);
    }
    async function removeFavorite(lessonId, button) {
      if (!current || !account.user) return;
      const userId = account.user.id, notice = $('favoriteNotice');
      button.disabled = true; notice.hidden = true;
      try {
        await account.request('/api/user/favorites/' + encodeURIComponent(lessonId), { method: 'DELETE' });
        if (!account.user || account.user.id !== userId) return;
        current.favorites = (Array.isArray(current.favorites) ? current.favorites : [])
          .filter(favorite => favorite && favorite.lessonId !== lessonId);
        renderFavorites();
      } catch (error) {
        if (account.user && account.user.id === userId) {
          button.disabled = false;
          notice.textContent = error.message || '取消收藏失败，请稍后重试。'; notice.hidden = false;
        }
      }
    }
    function render(data) {
      current = data;
      const stats = data.stats;
      $('userAvatar').textContent = Array.from(data.user.username)[0].toUpperCase();
      $('totalListening').textContent = formatDuration(stats.totalSeconds);
      $('effectiveEpisodes').textContent = stats.effectiveEpisodes;
      $('categoryCount').textContent = stats.categoryCount;
      $('dailyListening').textContent = formatDuration(stats.dailyAverageSeconds);
      $('averageBasis').textContent = stats.naturalDays ? '按首次收听起的 ' + stats.naturalDays + ' 个自然日' : '从首次实际收听开始统计';
      $('analysisEligibility').textContent = analysisStatus(stats.effectiveEpisodes, data.analysisConfigured);
      $('analysisProgress').textContent = '已完成 ' + stats.effectiveEpisodes + ' / 10 期有效收听';
      $('firstListeningDate').textContent = stats.firstListeningDate || '暂无记录';
      $('activeListeningDays').textContent = stats.activeDays + ' 天';
      $('preferredPeriod').textContent = stats.preferredPeriod;
      $('lastListeningDate').textContent = date(stats.lastListenedAt);
      $('timeZoneNote').textContent = '按 ' + stats.timeZone + ' 统计日期和时段；只呈现实际记录，不推断英语水平。';
      $('profileWelcome').hidden = stats.totalSeconds > 0;
      $('profileWelcome').textContent = data.records.length ? '已同步本机课程与进度；旧历史没有实际时长，新的学习积累将从现在开始记录。' : '欢迎开始你的英语精听旅程。播放第一期课程后，这里就会出现你的收听记录和学习积累。';
      const categories = data.categories.map(category => {
        const row = node('tr'), name = node('td', '', category.name);
        name.appendChild(node('span', 'user-record-sub', category.episodes + ' 期内容'));
        row.appendChild(name); row.appendChild(node('td', 'user-number', formatDuration(category.seconds)));
        row.appendChild(node('td', 'user-number', (category.share * 100).toFixed(1).replace(/\.0$/, '') + '%'));
        return row;
      });
      if (!categories.length) { const row = node('tr'), cell = node('td', 'user-empty', '收听后，这里会展示你的内容偏好。'); cell.colSpan = 3; row.appendChild(cell); categories.push(row); }
      $('userCategoryRows').replaceChildren(...categories);
      renderRecords(); renderFavorites(); renderAnalysis(data.analysis);
      $('profileLayout').hidden = false; $('profileStatus').hidden = true;
    }
    async function load(force = false) {
      const requestId = ++loadRequestId;
      $('guestPanel').hidden = Boolean(account.user);
      if (!account.user) {
        current = null; $('profileLayout').hidden = true; $('profileStatus').hidden = true; return;
      }
      if (current && current.user.id !== account.user.id) {
        current = null; $('profileLayout').hidden = true; $('profileStatus').hidden = false;
        $('profileStatus').textContent = '正在读取收听记录…';
      }
      $('refreshProfile').disabled = true;
      const id = account.user.id;
      try {
        const data = !force && account.profile ? account.profile : await account.sync();
        if (requestId === loadRequestId && data && account.user && account.user.id === id) render(data);
      } catch (_) {
        if (requestId === loadRequestId) {
          $('profileStatus').textContent = '暂时无法读取记录，请检查网络后刷新页面重试。'; $('profileStatus').hidden = false;
        }
      } finally { if (requestId === loadRequestId) $('refreshProfile').disabled = false; }
    }
    $('generateAnalysis').addEventListener('click', async () => {
      if (generating || !current || !account.user) return;
      const id = account.user.id, notice = $('analysisNotice'), episodes = current.stats.effectiveEpisodes;
      notice.hidden = false;
      if (episodes < 10) {
        notice.textContent = '你已完成 ' + episodes + ' 期有效收听，还差 ' + (10 - episodes) + ' 期。每期实际累计收听达到课程总时长的 80% 即可计入，继续保持！';
        return;
      }
      generating = true; $('generateAnalysis').disabled = true;
      notice.textContent = '正在整理你的兴趣与收听习惯，请稍候…';
      try {
        const result = await account.request('/api/user/analysis', { method: 'POST', timeout: 90000 });
        if (!account.user || account.user.id !== id) return;
        current.analysis = result; renderAnalysis(result); notice.hidden = true;
      } catch (error) {
        if (account.user && account.user.id === id) { notice.textContent = error.message || '分析暂时不可用，请稍后重试。'; notice.hidden = false; }
      } finally { generating = false; $('generateAnalysis').disabled = false; }
    });
    $('refreshProfile').addEventListener('click', () => load(true));
    $('moreUserRecords').addEventListener('click', () => { visibleRecords += 20; renderRecords(); });
    window.addEventListener('cadence:user-change', () => { visibleRecords = 20; $('analysisNotice').hidden = true; load(); });
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
    account.ready.then(() => load());
  }
  return { formatDuration, analysisStatus, mount };
});
