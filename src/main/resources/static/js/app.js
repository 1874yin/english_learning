(() => {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";
  const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5];
  const LEVELS = [
    { value: "Beginner", label: "初级" },
    { value: "Intermediate", label: "中级" },
    { value: "Advanced", label: "高级" }
  ];
  const HISTORY_LIMIT = 50;
  const LESSON_PAGE_SIZE = 60;
  const STORAGE_KEYS = {
    settings: "cadence.settings.v1",
    progress: "cadence.progress.v1",
    lastLesson: "cadence.last-lesson.v1",
    lastFeatured: "cadence.last-featured.v1",
    history: "cadence.history.v1"
  };
  const REDUCED_MOTION = window.matchMedia
    ? window.matchMedia("(prefers-reduced-motion: reduce)")
    : { matches: false };

  const byId = (id) => document.getElementById(id);

  const dom = {
    player: byId("player"),
    heroTitle: byId("lessonTitle"),
    heroTitleZh: byId("lessonTitleZh"),
    heroSummary: byId("lessonSummary"),
    heroSummaryZh: byId("lessonSummaryZh"),
    categoryPill: byId("categoryPill"),
    levelPill: byId("levelPill"),
    durationMeta: byId("durationMeta"),
    sentenceMeta: byId("sentenceMeta"),
    issueLabel: byId("issueLabel"),
    issueNumber: byId("issueNumber"),
    heroCover: byId("heroCover"),
    heroPlayBtn: byId("heroPlayBtn"),
    heroPracticeBtn: byId("heroPracticeBtn"),
    heroProgressText: byId("heroProgressText"),
    heroProgressFill: byId("heroProgressFill"),
    translationToggle: byId("translationToggle"),
    followToggle: byId("followToggle"),
    readerCard: byId("readerCard"),
    transcriptKicker: byId("transcriptKicker"),
    transcriptHeading: byId("transcriptHeading"),
    transcriptToggles: byId("transcriptToggles"),
    transcriptSubhead: byId("transcriptSubhead"),
    transcriptView: byId("transcriptView"),
    transcriptList: byId("transcriptList"),
    browseView: byId("browseView"),
    libraryView: byId("libraryView"),
    libraryPageHeading: byId("libraryPageHeading"),
    backToCategoriesBtn: byId("backToCategoriesBtn"),
    backToBrowseLabel: byId("backToBrowseLabel"),
    homeLevelFilters: byId("homeLevelFilters"),
    categoryGrid: byId("categoryGrid"),
    categoryStatus: byId("categoryStatus"),
    focusView: byId("focusView"),
    browseHeading: byId("browseHeading"),
    featuredCard: byId("featuredCard"),
    featuredHeading: byId("featuredHeading"),
    featuredTitle: byId("featuredTitle"),
    featuredTitleZh: byId("featuredTitleZh"),
    featuredSummary: byId("featuredSummary"),
    featuredSummaryZh: byId("featuredSummaryZh"),
    featuredCategory: byId("featuredCategory"),
    featuredLevel: byId("featuredLevel"),
    featuredDuration: byId("featuredDuration"),
    featuredSentence: byId("featuredSentence"),
    featuredIssue: byId("featuredIssue"),
    featuredIssueNumber: byId("featuredIssueNumber"),
    featuredCover: byId("featuredCover"),
    shuffleBtn: byId("shuffleBtn"),
    libraryHeading: byId("libraryHeading"),
    backToBrowseBtn: byId("backToBrowseBtn"),
    collapseFocusBtn: byId("collapseFocusBtn"),
    playerTrackBtn: byId("playerTrackBtn"),
    wordList: byId("wordList"),
    wordSection: byId("wordSection"),
    wordCount: byId("wordCount"),
    phraseList: byId("phraseList"),
    phraseSection: byId("phraseSection"),
    phraseCount: byId("phraseCount"),
    playlistCount: byId("playlistCount"),
    playlistFilters: byId("playlistFilters"),
    playlistSearch: byId("playlistSearch"),
    playlistSearchClear: byId("playlistSearchClear"),
    categoryFilters: byId("categoryFilters"),
    levelFilters: byId("levelFilters"),
    episodeList: byId("episodeList"),
    playlistMore: byId("playlistMore"),
    loadMoreBtn: byId("loadMoreBtn"),
    loadMoreStatus: byId("loadMoreStatus"),

    practicePanel: byId("practicePanel"),
    practiceCounter: byId("practiceCounter"),
    practiceEn: byId("practiceEn"),
    practiceZh: byId("practiceZh"),
    closePracticeBtn: byId("closePracticeBtn"),
    listenSentenceBtn: byId("listenSentenceBtn"),
    recordBtn: byId("recordBtn"),
    playRecordingBtn: byId("playRecordingBtn"),
    recordStatus: byId("recordStatus"),
    recordVisual: byId("recordVisual"),
    prevSentenceBtn: byId("prevSentenceBtn"),
    nextSentenceBtn: byId("nextSentenceBtn"),

    playerCover: byId("playerCover"),
    playerTitle: byId("playerTitle"),
    playerSubtitle: byId("playerSubtitle"),
    playerLevel: byId("playerLevel"),
    playerEq: byId("playerEq"),
    playBtn: byId("playBtn"),
    prevBtn: byId("prevBtn"),
    nextBtn: byId("nextBtn"),
    backBtn: byId("backBtn"),
    forwardBtn: byId("forwardBtn"),
    currentTime: byId("currentTime"),
    durationTime: byId("durationTime"),
    scrubber: byId("scrubber"),
    seekSlider: byId("seekSlider"),
    seekTooltip: byId("seekTooltip"),
    bufferBar: byId("bufferBar"),
    progressFill: byId("progressFill"),
    progressThumb: byId("progressThumb"),

    loopBtn: byId("loopBtn"),
    favoriteBtn: byId("favoriteBtn"),
    speedBtn: byId("speedBtn"),
    speedLabel: byId("speedLabel"),
    speedMenu: byId("speedMenu"),
    muteBtn: byId("muteBtn"),
    volumeSlider: byId("volumeSlider"),

    openShortcutsBtn: byId("openShortcutsBtn"),
    tipShortcutsBtn: byId("tipShortcutsBtn"),
    closeShortcutsBtn: byId("closeShortcutsBtn"),
    shortcutsDialog: byId("shortcutsDialog"),
    openHistoryBtn: byId("openHistoryBtn"),
    closeHistoryBtn: byId("closeHistoryBtn"),
    clearHistoryBtn: byId("clearHistoryBtn"),
    historyDialog: byId("historyDialog"),
    historyCount: byId("historyCount"),
    historyList: byId("historyList"),
    toast: byId("toast")
  };

  const audio = new Audio();
  audio.preload = "metadata";
  audio.preservesPitch = true;
  audio.mozPreservesPitch = true;
  audio.webkitPreservesPitch = true;

  const recordingAudio = new Audio();
  recordingAudio.preload = "auto";

  const storedSettingsValue = readStored(STORAGE_KEYS.settings, {});
  const storedSettings = storedSettingsValue && typeof storedSettingsValue === "object"
    ? storedSettingsValue
    : {};
  const storedProgress = readStored(STORAGE_KEYS.progress, {});
  const storedLastFeatured = readStored(STORAGE_KEYS.lastFeatured, "");
  const lastFeaturedId = typeof storedLastFeatured === "string" ? storedLastFeatured : "";
  const storedHistory = readStored(STORAGE_KEYS.history, []);
  const initialHistory = uniqueIds(
    Array.isArray(storedHistory)
      ? storedHistory.filter((id) => typeof id === "string" && id)
      : []
  ).slice(0, HISTORY_LIMIT);

  const state = {
    lessons: [],
    lessonTotal: 0,
    lessonPage: -1,
    lessonPageSize: LESSON_PAGE_SIZE,
    hasMoreLessons: false,
    loadingLessons: false,
    lessonLoadFailed: false,
    ensureAllPromise: null,
    lessonDetails: new Map(),
    currentLessonId: "",
    categoryOptions: [],
    homeLevel: "",
    categoryRequestId: 0,
    lessonRequestId: 0,
    history: initialHistory,
    filters: { category: "", level: "", query: "" },
    currentIndex: -1,
    activeLineIndex: 0,
    view: "browse",
    returnView: "browse",
    scrollPositions: { browse: 0, library: 0 },
    practiceIndex: 0,
    practiceOpen: false,
    practiceOpener: null,
    isPlaying: false,
    isSeeking: false,
    duration: 0,
    speed: SPEEDS.indexOf(Number(storedSettings.speed)) >= 0 ? Number(storedSettings.speed) : 1,
    volume: clamp(Number(storedSettings.volume), 0, 1, 0.9),
    muted: Boolean(storedSettings.muted),
    translation: storedSettings.translation !== false,
    follow: storedSettings.follow !== false,
    loopSentence: false,
    loopLineIndex: null,
    favorites: new Set(),
    favoriteBusy: false,
    segmentEnd: null,
    pendingSeek: null,
    pendingResumeToast: false,
    autoPlayOnMetadata: false,
    loadingAudio: false,
    progress: storedProgress && typeof storedProgress === "object" && !Array.isArray(storedProgress)
      ? storedProgress
      : {},
    recordings: new Map(),
    recorder: null,
    recordingStream: null,
    recordingTarget: null,
    isRecording: false,
    micPending: false,
    micRequestId: 0,
    recordTimer: null,
    recordStartedAt: 0,
    recordingPlaybackKey: null,
    pointerFocusTarget: null,
    lastProgressSave: 0,
    lastVolume: 0.9,
    resumeToasted: new Set(),
    toastTimer: null
  };

  if (state.volume === 0) {
    state.lastVolume = 0.6;
  } else {
    state.lastVolume = state.volume;
  }

  function readStored(key, fallback) {
    try {
      const raw = window.localStorage.getItem(key);
      return raw === null ? fallback : JSON.parse(raw);
    } catch (error) {
      return fallback;
    }
  }

  function writeStored(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      /* Storage can be unavailable in private or restricted contexts. */
    }
  }

  function uniqueIds(ids) {
    const seen = new Set();
    return ids.filter((id) => {
      if (seen.has(id)) {
        return false;
      }
      seen.add(id);
      return true;
    });
  }

  function clamp(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return fallback;
    }
    return Math.min(max, Math.max(min, number));
  }

  function currentLesson() {
    if (!state.currentLessonId) {
      return null;
    }
    return state.lessonDetails.get(state.currentLessonId) || null;
  }

  function syncCurrentIndex() {
    state.currentIndex = state.lessons.findIndex(
      (lesson) => lesson && lesson.id === state.currentLessonId
    );
  }

  function currentLine() {
    const lesson = currentLesson();
    return lesson ? lesson.transcript[state.practiceIndex] || lesson.transcript[0] : null;
  }

  function formatTime(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) {
      return "0:00";
    }
    const whole = Math.floor(seconds + 0.001);
    const hours = Math.floor(whole / 3600);
    const minutes = Math.floor((whole % 3600) / 60);
    const remaining = whole % 60;
    if (hours > 0) {
      return hours + ":" + String(minutes).padStart(2, "0") + ":" + String(remaining).padStart(2, "0");
    }
    return minutes + ":" + String(remaining).padStart(2, "0");
  }

  function formatSpeed(speed) {
    return speed === 1 ? "1.0×" : String(speed) + "×";
  }

  function createIcon(name, className) {
    const svg = document.createElementNS(SVG_NS, "svg");
    svg.setAttribute("class", className || "icon");
    svg.setAttribute("aria-hidden", "true");
    svg.setAttribute("focusable", "false");
    const use = document.createElementNS(SVG_NS, "use");
    use.setAttribute("href", "#icon-" + name);
    svg.appendChild(use);
    return svg;
  }

  function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (typeof text === "string") {
      element.textContent = text;
    }
    return element;
  }

  function recordingKey(lessonId, index) {
    return lessonId + ":" + index;
  }

  function currentRecordingKey() {
    const lesson = currentLesson();
    return lesson ? recordingKey(lesson.id, state.practiceIndex) : "";
  }

  function getRecording(index) {
    const lesson = currentLesson();
    return lesson ? state.recordings.get(recordingKey(lesson.id, index)) : null;
  }

  function scrollBehavior() {
    return REDUCED_MOTION.matches ? "auto" : "smooth";
  }

  function showToast(message) {
    window.clearTimeout(state.toastTimer);
    dom.toast.textContent = message;
    dom.toast.classList.add("is-visible");
    state.toastTimer = window.setTimeout(() => {
      dom.toast.classList.remove("is-visible");
    }, 2300);
  }

  /* -------------------------------------------------------
     Boot
     ------------------------------------------------------- */

  const account = window.CadenceUser;
  const listeningRecorder = new window.CadenceListening.Recorder({ request: account.request });
  let activeAccountId = null;

  function applyAccountStorage(user, profile) {
    activeAccountId = user ? user.id : null;
    Object.assign(STORAGE_KEYS, window.CadenceListening.storageKeys(activeAccountId));
    const progress = readStored(STORAGE_KEYS.progress, {});
    state.progress = progress && typeof progress === "object" && !Array.isArray(progress) ? progress : {};
    if (profile) state.progress = window.CadenceListening.mergeProgress(state.progress, profile.records);
    const history = readStored(STORAGE_KEYS.history, []);
    state.history = uniqueIds(Array.isArray(history) ? history.filter(id => typeof id === "string" && id) : []).slice(0, HISTORY_LIMIT);
    state.resumeToasted.clear();
  }

  function updateFavoriteUi() {
    if (!dom.favoriteBtn) {
      return;
    }
    const lesson = currentLesson();
    const active = Boolean(lesson && state.favorites.has(lesson.id));
    dom.favoriteBtn.classList.toggle("is-active", active);
    dom.favoriteBtn.setAttribute("aria-pressed", String(active));
    dom.favoriteBtn.setAttribute("aria-label", active ? "取消收藏" : "收藏");
    dom.favoriteBtn.title = active ? "取消收藏" : "收藏";
    dom.favoriteBtn.disabled = !lesson || state.favoriteBusy || state.lessonLoadFailed;
    const outline = dom.favoriteBtn.querySelector(".js-favorite-outline");
    const filled = dom.favoriteBtn.querySelector(".js-favorite-filled");
    if (outline) outline.classList.toggle("is-hidden", active);
    if (filled) filled.classList.toggle("is-hidden", !active);
  }

  function syncFavorites(profile) {
    const source = profile || account.profile;
    state.favorites = new Set(
      source && Array.isArray(source.favorites)
        ? source.favorites.map((favorite) => favorite && favorite.lessonId).filter(Boolean)
        : []
    );
    updateFavoriteUi();
    if (account.user && !source) {
      account.sync().then((fresh) => {
        if (fresh) syncFavorites(fresh);
      }).catch(() => {});
    }
  }

  async function toggleFavorite() {
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    if (!account.user) {
      account.open("login");
      return;
    }
    if (state.favoriteBusy) {
      return;
    }
    const active = state.favorites.has(lesson.id);
    state.favoriteBusy = true;
    updateFavoriteUi();
    try {
      await account.request("/api/user/favorites/" + encodeURIComponent(lesson.id), {
        method: active ? "DELETE" : "PUT"
      });
      if (active) {
        state.favorites.delete(lesson.id);
      } else {
        state.favorites.add(lesson.id);
      }
      showToast(active ? "已取消收藏" : "已收藏，可在个人主页查看");
    } catch (error) {
      if (error.status === 401) {
        showToast("请先登录后再收藏");
        account.open("login");
      } else {
        showToast(error.message || "收藏操作失败，请稍后重试。");
      }
    } finally {
      state.favoriteBusy = false;
      updateFavoriteUi();
    }
  }

  function sampleListening() {
    if (!activeAccountId || !account.user || account.user.id !== activeAccountId) {
      listeningRecorder.resetClock();
      return;
    }
    const lesson = currentLesson();
    if (!lesson) return;
    if (!listeningRecorder.current || listeningRecorder.current.lessonId !== lesson.id) {
      if (audio.paused || state.loadingAudio) return;
      listeningRecorder.start(lesson.id);
    }
    listeningRecorder.sample({ now: performance.now(), position: audio.currentTime, rate: audio.playbackRate,
      playing: !audio.paused && !audio.ended, seeking: audio.seeking || state.isSeeking,
      ready: audio.readyState >= 3 && !state.loadingAudio });
  }

  async function flushListening(options) {
    if (!activeAccountId || !account.user || account.user.id !== activeAccountId) return;
    const message = byId("listeningSyncMessage");
    try {
      await listeningRecorder.flush(options);
      if (message) message.hidden = true;
    } catch (error) {
      if (error.status === 404) listeningRecorder.reset();
      if (message && account.user) {
        message.textContent = "收听记录暂未同步，正在重试；播放进度仍保留在本机。";
        message.hidden = false;
      }
    }
  }

  function bindAccountListening() {
    ["playing", "timeupdate", "pause", "ended", "seeking", "seeked", "ratechange", "waiting", "stalled"]
      .forEach(event => audio.addEventListener(event, () => {
        sampleListening();
        if (["pause", "ended", "seeked"].includes(event)) flushListening();
      }));
    window.setInterval(() => { sampleListening(); flushListening(); }, 10000);
    window.addEventListener("pagehide", () => { sampleListening(); flushListening({ keepalive: true }); });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") { sampleListening(); flushListening({ keepalive: true }); }
    });
    account.beforeChange(async () => {
      saveProgress(); audio.pause(); sampleListening();
      try { await listeningRecorder.prepareForAccountChange(); }
      catch (error) { throw new Error("收听记录尚未同步，请恢复网络后再退出或切换账号。"); }
    });
    window.addEventListener("cadence:user-change", event => {
      audio.pause(); listeningRecorder.reset();
      applyAccountStorage(event.detail.user, event.detail.profile);
      syncFavorites(event.detail.profile);
      const lesson = currentLesson();
      if (lesson) {
        const progress = state.progress[lesson.id];
        const position = progress && !progress.completed ? Number(progress.position) || 0 : 0;
        if (state.loadingAudio) state.pendingSeek = position;
        else seekAudioTo(Math.min(position, Math.max(0, state.duration - 0.2)));
      }
      syncListenedMarks(); updateHeroProgress();
      if (dom.historyDialog.open) renderHistory();
    });
  }

  async function init() {
    await account.ready;
    applyAccountStorage(account.user, account.profile);
    syncFavorites(account.profile);
    audio.volume = state.volume;
    audio.muted = state.muted;
    audio.playbackRate = state.speed;
    recordingAudio.volume = Math.max(0.1, state.volume);

    applyToggles();
    updateSpeedUi();
    updateVolumeUi();
    updateLoopUi();
    updatePlayState();
    setView("browse", { focus: false, restoreScroll: false });
    bindAccountListening();
    bindEvents();
    renderHomeLevelFilters();
    loadHomeCategories();
    restoreLibraryRoute({ focus: false });
    setupMediaSessionHandlers();
    loadLessons();
  }

  function applyToggles() {
    dom.translationToggle.classList.toggle("is-on", state.translation);
    dom.translationToggle.setAttribute("aria-pressed", String(state.translation));
    dom.followToggle.classList.toggle("is-on", state.follow);
    dom.followToggle.setAttribute("aria-pressed", String(state.follow));
    dom.transcriptList.classList.toggle("hide-translation", !state.translation);
  }

  function persistSettings() {
    writeStored(STORAGE_KEYS.settings, {
      speed: state.speed,
      volume: state.volume,
      muted: state.muted,
      translation: state.translation,
      follow: state.follow
    });
  }

  function isUsableLesson(lesson) {
    return Boolean(lesson && lesson.id && lesson.audio
      && Array.isArray(lesson.transcript) && lesson.transcript.length);
  }

  function lessonSummary(lesson) {
    return {
      id: lesson.id,
      number: lesson.number,
      title: lesson.title,
      titleZh: lesson.titleZh,
      category: lesson.category,
      level: lesson.level,
      duration: lesson.duration,
      cover: lesson.cover
    };
  }

  /**
   * A deep-linked lesson can live on a page that was not loaded yet. Its
   * summary is inserted at the top so the playlist can highlight it; pages
   * loaded later skip the duplicate.
   */
  function ensureLessonSummary(lesson) {
    if (!lesson || !lesson.id) {
      return;
    }
    if (state.lessons.some((item) => item && item.id === lesson.id)) {
      return;
    }
    state.lessons = [lessonSummary(lesson)].concat(state.lessons);
    syncCurrentIndex();
  }

  async function fetchLessonJson(url) {
    const response = await fetch(url, {
      headers: { Accept: "application/json" }
    });
    if (!response.ok) {
      const error = new Error("Request failed with " + response.status);
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  async function fetchLessonDetail(id) {
    try {
      return await fetchLessonJson("/api/lessons/" + encodeURIComponent(id));
    } catch (error) {
      if (error && error.status === 404) {
        return null;
      }
      throw error;
    }
  }

  async function fetchLessonPage(page) {
    const params = new URLSearchParams({
      page: String(page),
      size: String(state.lessonPageSize)
    });
    return fetchLessonJson("/api/lessons?" + params.toString());
  }

  async function loadLessonPage(page, options) {
    const opts = Object.assign({ render: true }, options || {});
    if (state.loadingLessons) {
      return false;
    }
    state.loadingLessons = true;
    updateLoadMoreUi();
    try {
      const data = await fetchLessonPage(page);
      const items = Array.isArray(data && data.items)
        ? data.items.filter((item) => item && item.id && item.title)
        : [];
      state.lessons = page === 0
        ? items
        : window.CadenceLibrary.mergeLessonSummaries(state.lessons, items);
      state.lessonTotal = Number(data && data.total) || state.lessons.length;
      state.lessonPage = data && Number.isInteger(data.page) ? data.page : page;
      state.lessonPageSize = data && Number(data.size)
        ? Number(data.size)
        : state.lessonPageSize;
      state.hasMoreLessons = Boolean(data && data.hasMore);
      if (page === 0) {
        state.lessonLoadFailed = false;
      }
      syncCurrentIndex();
      if (opts.render) {
        renderPlaylist();
      } else {
        updateLoadMoreUi();
      }
      return true;
    } finally {
      state.loadingLessons = false;
      updateLoadMoreUi();
    }
  }

  async function loadMoreLessons() {
    if (!state.hasMoreLessons || state.loadingLessons) {
      return false;
    }
    try {
      return await loadLessonPage(state.lessonPage + 1);
    } catch (error) {
      console.error(error);
      showToast("加载更多素材失败，请稍后重试。");
      return false;
    }
  }

  /**
   * Search, filters and "换一期" need the whole summary index. Summaries are
   * small (no transcripts), so the remaining pages are fetched on demand
   * instead of blocking the first render.
   */
  async function ensureAllLessons() {
    if (!state.hasMoreLessons) {
      return;
    }
    if (!state.ensureAllPromise) {
      state.ensureAllPromise = (async () => {
        try {
          while (state.hasMoreLessons) {
            const loaded = await loadLessonPage(state.lessonPage + 1, { render: false });
            if (!loaded) {
              break;
            }
          }
        } catch (error) {
          console.error(error);
        } finally {
          state.ensureAllPromise = null;
          renderPlaylist();
        }
      })();
    }
    return state.ensureAllPromise;
  }

  function renderHomeLevelFilters() {
    renderFilterChips(dom.homeLevelFilters, LEVELS, state.homeLevel, (level) => {
      if (state.homeLevel === level) {
        return;
      }
      state.homeLevel = level;
      renderHomeLevelFilters();
      loadHomeCategories();
    });
  }

  async function loadHomeCategories() {
    const requestId = ++state.categoryRequestId;
    dom.categoryGrid.setAttribute("aria-busy", "true");
    dom.categoryGrid.replaceChildren();
    dom.categoryStatus.textContent = "正在加载分类…";
    try {
      const params = new URLSearchParams();
      if (state.homeLevel) {
        params.set("level", state.homeLevel);
      }
      const categories = await fetchLessonJson("/api/categories/browse?" + params.toString());
      if (requestId !== state.categoryRequestId) {
        return;
      }
      const fragment = document.createDocumentFragment();
      categories.forEach((category, index) => {
        const card = createElement("a", "category-card");
        card.href = window.CadenceLibrary.buildLibraryHash({
          category: category.name, level: state.homeLevel, query: ""
        });
        card.dataset.category = category.name;
        const top = createElement("span", "category-card-top");
        top.appendChild(createIcon("headphones"));
        top.appendChild(createElement("span", "category-card-index", String(index + 1).padStart(2, "0")));
        const name = createElement("h3", "category-card-name", category.name);
        const footer = createElement("span", "category-card-footer");
        footer.appendChild(createElement("span", "", category.count + " 期音频"));
        footer.appendChild(createIcon("arrow-right"));
        card.append(top, name, footer);
        fragment.appendChild(card);
      });
      dom.categoryGrid.replaceChildren(fragment);
      dom.categoryStatus.textContent = categories.length
        ? "选择分类，探索" + (state.homeLevel ? "适合当前难度的" : "你感兴趣的") + "音频。"
        : (state.homeLevel ? "这个难度下还没有素材，试试其他难度吧。" : "还没有学习素材，上传后即可按分类浏览。");
    } catch (error) {
      if (requestId !== state.categoryRequestId) {
        return;
      }
      console.error(error);
      dom.categoryStatus.textContent = "分类加载失败，请稍后重试。";
      const retry = createElement("button", "load-retry", "重新加载分类");
      retry.type = "button";
      retry.addEventListener("click", loadHomeCategories);
      dom.categoryGrid.replaceChildren(retry);
    } finally {
      if (requestId === state.categoryRequestId) {
        dom.categoryGrid.setAttribute("aria-busy", "false");
      }
    }
  }

  function updateLibraryRoute(replace) {
    const url = new URL(window.location.href);
    url.hash = window.CadenceLibrary.buildLibraryHash(state.filters);
    window.history[replace ? "replaceState" : "pushState"](null, "", url.toString());
  }

  function openCategory(category) {
    state.filters = { category: category, level: state.homeLevel, query: "" };
    dom.playlistSearch.value = "";
    updateSearchClearButton();
    updateLibraryRoute(false);
    setView("library", { restoreScroll: false });
    if (state.lessonLoadFailed) {
      loadLessons();
    } else {
      refreshFilteredPlaylist();
    }
  }

  function returnToCategories() {
    const url = new URL(window.location.href);
    url.hash = "";
    window.history.pushState(null, "", url.toString());
    setView("browse");
  }

  function restoreLibraryRoute(options) {
    const filters = window.CadenceLibrary.parseLibraryHash(window.location.hash);
    if (filters) {
      state.filters = filters;
      dom.playlistSearch.value = filters.query;
      updateSearchClearButton();
      setView("library", options);
      if (state.lessonTotal) {
        refreshFilteredPlaylist();
      }
    } else {
      setView("browse", options);
    }
  }

  async function loadCategoryOptions() {
    try {
      const data = await fetchLessonJson("/api/categories");
      if (Array.isArray(data)) {
        state.categoryOptions = data.filter(
          (item) => typeof item === "string" && item.trim()
        );
        renderPlaylistFilters();
      }
    } catch (error) {
      console.error(error);
    }
  }

  async function loadInitialLesson(requestedId) {
    if (requestedId) {
      try {
        const requested = await fetchLessonDetail(requestedId);
        if (isUsableLesson(requested)) {
          return requested;
        }
      } catch (error) {
        console.error(error);
      }
    }

    const lastIndex = lastFeaturedId
      ? state.lessons.findIndex((lesson) => lesson.id === lastFeaturedId)
      : -1;
    const index = window.CadenceLibrary.pickRandomIndex(state.lessons.length, lastIndex);
    if (index < 0) {
      return null;
    }
    return fetchLessonDetail(state.lessons[index].id);
  }

  async function loadLessons() {
    try {
      await loadLessonPage(0);
      if (!state.lessons.length) {
        showEmptyLibrary();
        return;
      }

      setLibraryControlsEnabled(true);
      loadCategoryOptions();
      if (state.view === "library" && window.CadenceLibrary.hasActiveFilters(state.filters)) {
        ensureAllLessons();
      }

      const params = new URLSearchParams(window.location.search);
      const lesson = await loadInitialLesson(params.get("lesson"));
      if (!isUsableLesson(lesson)) {
        showEmptyLibrary();
        return;
      }
      openLesson(lesson, { autoplay: false, scrollToTop: false, enterFocus: params.get("from") === "profile" });
      writeStored(STORAGE_KEYS.lastFeatured, lesson.id);
    } catch (error) {
      console.error(error);
      showLoadError();
    }
  }

  function updateLoadMoreUi() {
    if (!dom.playlistMore) {
      return;
    }
    const visible = Boolean(state.hasMoreLessons);
    dom.playlistMore.hidden = !visible;
    if (!visible) {
      return;
    }
    dom.loadMoreBtn.disabled = state.loadingLessons;
    dom.loadMoreBtn.textContent = state.loadingLessons ? "正在加载…" : "加载更多";
    dom.loadMoreStatus.textContent = "已显示 " + state.lessons.length
      + " / " + state.lessonTotal + " 期";
  }

  function showLoadError() {
    state.lessonLoadFailed = true;
    dom.playlistFilters.hidden = true;
    dom.featuredCard.hidden = true;
    dom.featuredHeading.hidden = true;
    dom.libraryHeading.textContent = "课程加载失败";
    dom.transcriptList.replaceChildren();
    const message = createElement("div", "load-error");
    message.appendChild(createElement("strong", "", "课程加载失败"));
    message.appendChild(createElement("p", "", "请确认服务已启动，然后重新加载页面。"));
    const retry = createElement("button", "load-retry", "重新加载");
    retry.type = "button";
    retry.addEventListener("click", () => window.location.reload());
    message.appendChild(retry);
    dom.transcriptList.appendChild(message);

    dom.episodeList.replaceChildren();
    dom.playlistMore.hidden = true;
    const playlistMessage = createElement("div", "playlist-empty");
    playlistMessage.appendChild(createElement("p", "", "暂时无法获取音频列表。"));
    const playlistRetry = createElement("button", "playlist-filter-clear", "重新加载");
    playlistRetry.type = "button";
    playlistRetry.addEventListener("click", () => window.location.reload());
    playlistMessage.appendChild(playlistRetry);
    dom.episodeList.appendChild(playlistMessage);
    setLibraryControlsEnabled(false);
    showToast("课程加载失败，请稍后重试。");
  }

  function setLibraryControlsEnabled(enabled) {
    [
      dom.heroPlayBtn,
      dom.heroPracticeBtn,
      dom.shuffleBtn,
      dom.playBtn,
      dom.prevBtn,
      dom.nextBtn,
      dom.backBtn,
      dom.forwardBtn,
      dom.loopBtn,
      dom.favoriteBtn,
      dom.speedBtn,
      dom.muteBtn,
      dom.translationToggle,
      dom.followToggle,
      dom.playerTrackBtn
    ].forEach((control) => {
      if (control) {
        control.disabled = !enabled;
      }
    });
    dom.seekSlider.disabled = !enabled;
    dom.volumeSlider.disabled = !enabled;
  }

  function showEmptyLibrary() {
    state.currentIndex = -1;
    state.currentLessonId = "";
    state.lessons = [];
    state.lessonTotal = 0;
    state.lessonPage = -1;
    state.hasMoreLessons = false;
    state.activeLineIndex = 0;
    state.practiceIndex = 0;
    state.filters.category = "";
    state.filters.level = "";
    state.filters.query = "";
    if (dom.playlistSearch) {
      dom.playlistSearch.value = "";
    }
    updateSearchClearButton();
    dom.playlistFilters.hidden = true;
    dom.featuredCard.hidden = true;
    dom.featuredHeading.hidden = true;
    dom.libraryHeading.textContent = "还没有学习素材";
    dom.categoryFilters.replaceChildren();
    dom.levelFilters.replaceChildren();
    state.duration = 0;
    state.loadingAudio = false;
    state.pendingSeek = null;
    state.isPlaying = false;
    audio.pause();
    setLibraryControlsEnabled(false);
    updateFavoriteUi();

    dom.playerCover.src = "/images/cover-default.svg";
    dom.playerTitle.textContent = "素材库为空";
    dom.playerSubtitle.textContent = "上传 MP3 和台词后会出现在这里";
    dom.playerLevel.textContent = "—";
    dom.currentTime.textContent = "0:00";
    dom.durationTime.textContent = "0:00";
    dom.progressFill.style.width = "0%";
    dom.progressThumb.style.left = "0%";
    dom.bufferBar.style.width = "0%";
    dom.seekSlider.max = "0";
    dom.seekSlider.value = "0";
    dom.heroPlayBtn.querySelector(".js-play-label").textContent = "开始精听";
    dom.wordSection.hidden = true;
    dom.wordList.replaceChildren();
    dom.wordCount.textContent = "0 个词条";
    dom.phraseSection.hidden = true;
    dom.translationToggle.hidden = true;
    document.title = "还没有学习素材 · Cadence 英语精听";

    dom.transcriptList.replaceChildren();
    dom.episodeList.replaceChildren();
    dom.playlistMore.hidden = true;
    dom.episodeList.appendChild(createElement("p", "playlist-empty", "添加素材后，音频列表会显示在这里。"));
    dom.playlistCount.textContent = "0 期";

    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = null;
      navigator.mediaSession.playbackState = "none";
    }
  }

  /* -------------------------------------------------------
     Lesson selection and rendering
     ------------------------------------------------------- */

  async function selectLesson(index, options) {
    const opts = Object.assign({
      autoplay: false,
      scrollToTop: false,
      restore: true,
      enterFocus: false
    }, options || {});
    if (!state.lessons.length || index < 0 || index >= state.lessons.length) {
      return null;
    }

    const summary = state.lessons[index];
    const current = currentLesson();
    if (current && current.id === summary.id && audio.getAttribute("src")) {
      if (opts.autoplay && audio.paused) {
        togglePlay();
      }
      if (opts.scrollToTop) {
        scrollPageTop();
      }
      if (opts.enterFocus) {
        enterFocus();
      }
      return current;
    }

    const requestId = state.lessonRequestId + 1;
    state.lessonRequestId = requestId;

    let lesson = state.lessonDetails.get(summary.id);
    if (!lesson) {
      try {
        lesson = await fetchLessonDetail(summary.id);
      } catch (error) {
        console.error(error);
        showToast("课程加载失败，请稍后重试。");
        return null;
      }
      if (requestId !== state.lessonRequestId) {
        return null;
      }
      if (!isUsableLesson(lesson)) {
        showToast("课程加载失败，请稍后重试。");
        return null;
      }
      state.lessonDetails.set(lesson.id, lesson);
    }

    openLesson(lesson, opts);
    return lesson;
  }

  function openLesson(lesson, options) {
    if (!isUsableLesson(lesson)) {
      return;
    }
    state.lessonRequestId += 1;
    state.lessonDetails.set(lesson.id, lesson);
    ensureLessonSummary(lesson);
    applyLessonSelection(lesson, options);
  }

  function applyLessonSelection(lesson, options) {
    const opts = Object.assign({
      autoplay: false,
      scrollToTop: false,
      restore: true,
      enterFocus: false
    }, options || {});

    saveProgress();
    state.loadingAudio = true;
    if (state.practiceOpen) {
      closePractice({ pauseAudio: false, focus: false, silent: true });
    }
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    stopRecordingPlayback();
    audio.pause();

    state.currentLessonId = lesson.id;
    state.lessonDetails.set(lesson.id, lesson);
    syncCurrentIndex();
    state.activeLineIndex = -1;
    state.practiceIndex = 0;
    state.segmentEnd = null;
    state.loopLineIndex = null;
    state.isSeeking = false;

    state.duration = Number(lesson.duration) || 0;

    let resume = 0;
    if (opts.restore !== false) {
      const saved = state.progress[lesson.id];
      if (saved && !saved.completed) {
        const duration = Number(lesson.duration) || 0;
        const position = Number(saved.position) || 0;
        if (position > 3 && (!duration || position < duration - 2)) {
          resume = position;
        }
      }
    }

    state.pendingSeek = resume > 0 ? resume : null;
    state.pendingResumeToast = resume > 0;
    state.autoPlayOnMetadata = Boolean(opts.autoplay);

    renderLesson(lesson);
    writeStored(STORAGE_KEYS.lastLesson, lesson.id);
    updateUrl(lesson.id);

    audio.src = lesson.audio;
    audio.load();

    if (opts.scrollToTop) {
      scrollPageTop();
    }
    if (opts.enterFocus) {
      enterFocus();
    }
  }

  function scrollPageTop() {
    window.scrollTo({ top: 0, behavior: scrollBehavior() });
  }

  function updateUrl(lessonId) {
    try {
      const url = new URL(window.location.href);
      url.searchParams.set("lesson", lessonId);
      window.history.replaceState(null, "", url.toString());
    } catch (error) {
      /* file:// or restricted history should not break the app */
    }
  }

  function renderLesson(lesson) {
    setLibraryControlsEnabled(true);
    const durationLabel = formatTime(Number(lesson.duration) || 0);
    const sentenceCount = String(lesson.transcript.length);
    const issueNumber = lesson.number || "01";

    dom.heroTitle.textContent = lesson.title;
    dom.heroTitleZh.textContent = lesson.titleZh || "";
    dom.heroTitleZh.hidden = !lesson.titleZh;
    dom.heroSummary.textContent = lesson.summary || "";
    dom.heroSummary.hidden = !lesson.summary;
    dom.heroSummaryZh.textContent = lesson.summaryZh || "";
    dom.heroSummaryZh.hidden = !lesson.summaryZh;
    dom.categoryPill.textContent = lesson.category || "";
    dom.levelPill.textContent = lesson.level || "";
    dom.durationMeta.textContent = durationLabel;
    dom.sentenceMeta.textContent = sentenceCount;

    dom.featuredTitle.textContent = lesson.title;
    dom.featuredTitleZh.textContent = lesson.titleZh || "";
    dom.featuredTitleZh.hidden = !lesson.titleZh;
    dom.featuredSummary.textContent = lesson.summary || "";
    dom.featuredSummary.hidden = !lesson.summary;
    dom.featuredSummaryZh.textContent = lesson.summaryZh || "";
    dom.featuredSummaryZh.hidden = !lesson.summaryZh;
    dom.featuredCategory.textContent = lesson.category || "";
    dom.featuredLevel.textContent = lesson.level || "";
    dom.featuredDuration.textContent = durationLabel;
    dom.featuredSentence.textContent = sentenceCount;
    dom.featuredIssue.textContent = "ISSUE " + issueNumber;
    dom.featuredIssueNumber.textContent = issueNumber;
    const hasTranslation = lesson.transcript.some((line) => line.translation
      && line.translation.trim());
    dom.translationToggle.hidden = !hasTranslation;
    if (dom.issueLabel) {
      dom.issueLabel.textContent = "ISSUE " + issueNumber;
    }
    dom.issueNumber.textContent = issueNumber;

    const cover = lesson.cover || "/images/cover-default.svg";
    const coverAlt = (lesson.titleZh || lesson.title || "课程") + "封面";
    dom.heroCover.src = cover;
    dom.heroCover.alt = coverAlt;
    dom.featuredCover.src = cover;
    dom.featuredCover.alt = coverAlt;
    dom.playerCover.src = cover;
    dom.playerTitle.textContent = lesson.title;
    dom.playerSubtitle.textContent = lesson.titleZh || "";
    dom.playerLevel.textContent = lesson.levelZh || lesson.level || "";
    dom.durationTime.textContent = formatTime(Number(lesson.duration) || 0);
    dom.currentTime.textContent = "0:00";
    dom.progressFill.style.width = "0%";
    dom.progressThumb.style.left = "0%";
    dom.bufferBar.style.width = "0%";
    dom.seekSlider.max = String(Number(lesson.duration) || 0);
    dom.seekSlider.value = "0";
    dom.seekSlider.disabled = !Number(lesson.duration);

    document.title = lesson.title + " · Cadence 英语精听";
    applyToggles();
    renderTranscript(lesson);
    renderWords(lesson);
    renderPhrases(lesson);
    renderPlaylist();
    updatePlayState();
    updateHeroProgress();
    updateMediaMetadata();
    updateLoopUi();
    updateFavoriteUi();

    dom.heroPlayBtn.querySelector(".js-play-label").textContent = "开始精听";
    dom.transcriptList.scrollTop = 0;

    const previewIndex = state.pendingSeek
      ? findActiveLine(state.pendingSeek, lesson)
      : 0;
    setActiveLine(previewIndex, { scroll: false, fromPractice: true });
  }

  function renderTranscript(lesson) {
    const fragment = document.createDocumentFragment();

    lesson.transcript.forEach((line, index) => {
      const sentence = createElement("div", "sentence");
      sentence.dataset.index = String(index);

      const timeButton = createElement("button", "sentence-time");
      timeButton.type = "button";
      timeButton.dataset.action = "seek";
      timeButton.setAttribute("aria-label", "从 " + formatTime(line.start) + " 开始播放第 " + (index + 1) + " 句");
      timeButton.appendChild(createElement("span", "sentence-time-text", formatTime(line.start)));
      const miniPlay = createIcon("play", "mini-play");
      timeButton.appendChild(miniPlay);

      const main = createElement("div", "sentence-main");
      main.dataset.action = "seek";
      main.tabIndex = 0;
      main.setAttribute("role", "button");
      main.setAttribute("aria-label", "播放第 " + (index + 1) + " 句：" + line.text);
      const english = createElement("p", "sentence-en", line.text);
      english.lang = "en";
      const chinese = createElement("p", "sentence-zh", line.translation || "");
      main.appendChild(english);
      if (line.translation) {
        main.appendChild(chinese);
      }

      const actions = createElement("div", "sentence-actions");
      const loopAction = createElement("button", "sentence-action");
      loopAction.type = "button";
      loopAction.dataset.action = "loop";
      loopAction.title = "循环本句";
      loopAction.setAttribute("aria-label", "循环第 " + (index + 1) + " 句");
      loopAction.appendChild(createIcon("repeat"));

      const practiceAction = createElement("button", "sentence-action");
      practiceAction.type = "button";
      practiceAction.dataset.action = "practice";
      practiceAction.title = "跟读本句";
      practiceAction.setAttribute("aria-label", "跟读第 " + (index + 1) + " 句");
      practiceAction.appendChild(createIcon("mic"));
      if (getRecording(index)) {
        practiceAction.classList.add("is-recorded");
      }

      actions.appendChild(loopAction);
      actions.appendChild(practiceAction);
      sentence.appendChild(timeButton);
      sentence.appendChild(main);
      sentence.appendChild(actions);
      fragment.appendChild(sentence);
    });

    dom.transcriptList.replaceChildren(fragment);
  }

  function renderWords(lesson) {
    const text = typeof lesson.wordsText === "string" ? lesson.wordsText.trim() : "";
    dom.wordList.textContent = text;
    const entries = text ? text.split(/\r?\n/).filter((line) => line.trim()) : [];
    dom.wordCount.textContent = entries.length + " 个词条";
    dom.wordSection.hidden = entries.length === 0;
  }

  function renderPhrases(lesson) {
    dom.phraseList.replaceChildren();
    const phrases = Array.isArray(lesson.keyPhrases) ? lesson.keyPhrases : [];
    dom.phraseCount.textContent = phrases.length + " 个表达";
    dom.phraseSection.hidden = phrases.length === 0;

    phrases.forEach((phrase, index) => {
      const card = createElement("article", "phrase-card");
      card.appendChild(createElement("span", "phrase-number", String(index + 1).padStart(2, "0") + " / " + String(phrases.length).padStart(2, "0")));
      card.appendChild(createElement("h3", "phrase-word", phrase.phrase || ""));
      if (phrase.phonetic) {
        card.appendChild(createElement("p", "phrase-phonetic", phrase.phonetic));
      }
      if (phrase.meaning) {
        card.appendChild(createElement("p", "phrase-meaning", phrase.meaning));
      }
      if (phrase.example) {
        card.appendChild(createElement("p", "phrase-example", phrase.example));
      }
      dom.phraseList.appendChild(card);
    });
  }

  function lessonCategory(lesson) {
    return typeof lesson.category === "string" ? lesson.category.trim() : "";
  }

  function lessonLevel(lesson) {
    return typeof lesson.level === "string" ? lesson.level.trim() : "";
  }

  function matchesFilters(lesson) {
    if (!window.CadenceLibrary.matchesCategory(lessonCategory(lesson), state.filters.category)) {
      return false;
    }
    if (state.filters.level
      && lessonLevel(lesson).toLowerCase() !== state.filters.level.toLowerCase()) {
      return false;
    }
    return window.CadenceSearch.matchesLessonSearch(lesson, state.filters.query);
  }

  function updateSearchClearButton() {
    if (dom.playlistSearchClear) {
      dom.playlistSearchClear.hidden = !state.filters.query.trim();
    }
  }

  function clearFilters() {
    state.filters.category = "";
    state.filters.level = "";
    state.filters.query = "";
    if (dom.playlistSearch) {
      dom.playlistSearch.value = "";
    }
    updateSearchClearButton();
    renderPlaylist();
  }

  function focusFilterChip(container, value) {
    const chips = container.querySelectorAll(".filter-chip");
    for (let i = 0; i < chips.length; i += 1) {
      if ((chips[i].dataset.value || "") === value) {
        chips[i].focus({ preventScroll: true });
        return;
      }
    }
  }

  function renderFilterChips(container, options, activeValue, onSelect) {
    const focusedChip = container.contains(document.activeElement)
      ? document.activeElement
      : null;
    const focusedValue = focusedChip && focusedChip.dataset
      ? focusedChip.dataset.value
      : null;
    container.replaceChildren();
    const all = createElement(
      "button",
      "filter-chip" + (activeValue ? "" : " is-active"),
      "全部"
    );
    all.type = "button";
    all.dataset.value = "";
    all.setAttribute("aria-pressed", String(!activeValue));
    all.addEventListener("click", () => onSelect(""));
    container.appendChild(all);

    options.forEach((option) => {
      const active = activeValue && option.value.toLowerCase() === activeValue.toLowerCase();
      const chip = createElement(
        "button",
        "filter-chip" + (active ? " is-active" : ""),
        option.label
      );
      chip.type = "button";
      chip.dataset.value = option.value;
      chip.title = option.title || option.value;
      chip.setAttribute("aria-pressed", String(Boolean(active)));
      chip.addEventListener("click", () => onSelect(option.value));
      container.appendChild(chip);
    });

    if (focusedChip) {
      focusFilterChip(container, focusedValue || "");
    }
  }

  function distinctLessonValues(readValue) {
    const values = [];
    state.lessons.forEach((lesson) => {
      const value = readValue(lesson);
      if (value && !values.some((item) => item.toLowerCase() === value.toLowerCase())) {
        values.push(value);
      }
    });
    return values;
  }

  function renderPlaylistFilters() {
    const categories = state.categoryOptions.length
      ? state.categoryOptions
      : distinctLessonValues(lessonCategory);
    // The three admin-supported levels stay available even when the first page
    // does not contain one of them yet.
    const levelOptions = LEVELS.map((level) => ({
      value: level.value,
      label: level.label,
      title: level.value
    }));

    if (state.filters.level
      && !levelOptions.some((item) => item.value.toLowerCase() === state.filters.level.toLowerCase())) {
      state.filters.level = "";
    }

    dom.playlistFilters.hidden = !state.lessonTotal;
    renderFilterChips(
      dom.categoryFilters,
      categories.map((category) => ({ value: category, label: category })),
      state.filters.category,
      (value) => {
        if (state.filters.category === value) {
          return;
        }
        state.filters.category = value;
        refreshFilteredPlaylist();
      }
    );
    renderFilterChips(
      dom.levelFilters,
      levelOptions,
      state.filters.level,
      (value) => {
        if (state.filters.level === value) {
          return;
        }
        state.filters.level = value;
        refreshFilteredPlaylist();
      }
    );
  }

  function refreshFilteredPlaylist() {
    renderPlaylist();
    ensureAllLessons();
  }

  function renderPlaylist() {
    if (state.lessonLoadFailed) {
      return;
    }
    renderPlaylistFilters();
    if (state.view === "library") {
      updateLibraryRoute(true);
    }
    dom.libraryPageHeading.textContent = state.filters.category || "全部音频";
    if (!state.lessonTotal) {
      dom.libraryHeading.textContent = "全部音频";
      dom.playlistCount.textContent = "0 期";
      dom.featuredCard.hidden = true;
      dom.featuredHeading.hidden = true;
      dom.episodeList.replaceChildren();
      updateLoadMoreUi();
      return;
    }

    const filtering = window.CadenceLibrary.hasActiveFilters(state.filters);
    const searching = state.filters.query.trim() !== "";
    dom.featuredCard.hidden = false;
    dom.featuredHeading.hidden = false;
    dom.libraryHeading.textContent = searching ? "搜索结果" : "音频列表";
    const fragment = document.createDocumentFragment();
    const filtered = [];
    state.lessons.forEach((lesson, index) => {
      if (matchesFilters(lesson)) {
        filtered.push({ lesson: lesson, index: index });
      }
    });

    dom.playlistCount.textContent = filtering
      ? filtered.length + " / " + state.lessons.length + " 期"
      : state.lessonTotal + " 期";

    if (!filtered.length) {
      const empty = createElement("div", "playlist-empty playlist-empty--filtered");
      empty.appendChild(createElement("p", "", "没有找到匹配的素材。"));
      const clear = createElement("button", "playlist-filter-clear", searching ? "清除搜索和筛选" : "清除筛选");
      clear.type = "button";
      clear.addEventListener("click", clearFilters);
      empty.appendChild(clear);
      dom.episodeList.replaceChildren(empty);
      updateLoadMoreUi();
      return;
    }

    filtered.forEach((entry) => {
      const lesson = entry.lesson;
      const index = entry.index;
      const button = createElement("button", "episode"
        + (index === state.currentIndex ? " is-active" : "")
        + (hasListened(lesson.id) ? " is-listened" : ""));
      button.type = "button";
      button.dataset.index = String(index);
      if (index === state.currentIndex) {
        button.setAttribute("aria-current", "true");
      }
      if (index === state.currentIndex && state.isPlaying) {
        button.classList.add("is-playing");
      }

      const durationLabel = formatTime(Number(lesson.duration) || 0);
      const level = LEVELS.find((option) => option.value.toLowerCase() === lessonLevel(lesson).toLowerCase());
      const levelLabel = lesson.levelZh || (level ? level.label : lessonLevel(lesson));
      button.title = lesson.title + (lesson.titleZh ? "\n" + lesson.titleZh : "");
      button.setAttribute("aria-label", "第 " + (lesson.number || index + 1) + " 期：" + lesson.title
        + (lesson.titleZh ? "，" + lesson.titleZh : "") + "，" + levelLabel + "，" + durationLabel);

      const cover = createElement("span", "episode-cover");
      const image = document.createElement("img");
      image.src = lesson.cover || "/images/cover-default.svg";
      image.alt = "";
      image.loading = index === state.currentIndex ? "eager" : "lazy";
      image.decoding = "async";
      cover.appendChild(image);
      cover.appendChild(createElement("span", "episode-number", "EP. " + (lesson.number || String(index + 1).padStart(2, "0"))));

      const duration = createElement("span", "episode-duration");
      duration.append(createIcon("clock"), createElement("span", "", durationLabel));
      cover.appendChild(duration);
      const history = createElement("span", "episode-history");
      history.append(createIcon("check"), createElement("span", "", "听过"));
      cover.appendChild(history);

      const waves = createElement("span", "episode-waves");
      waves.setAttribute("aria-hidden", "true");
      for (let wave = 0; wave < 3; wave += 1) {
        waves.appendChild(createElement("span", "episode-wave"));
      }
      waves.appendChild(createElement("span", "episode-playing-label", "播放中"));
      cover.appendChild(waves);

      const copy = createElement("span", "episode-copy");
      const top = createElement("span", "episode-topline");
      const category = createElement("span", "episode-category", lesson.categoryZh || lesson.category || "");
      category.title = category.textContent;
      top.appendChild(category);
      if (levelLabel) {
        top.appendChild(createElement("span", "episode-level", levelLabel));
      }

      const title = createElement("span", "episode-title", lesson.title);
      const titleZh = createElement("span", "episode-zh", lesson.titleZh || "");
      const percent = getSavedPercent(lesson);
      const bottom = createElement("span", "episode-bottom");
      const progressGroup = createElement("span", "episode-progress-group");
      const progressLabel = createElement("span", "episode-progress-label", window.CadenceLibrary.formatListeningProgress(percent));
      const progress = createElement("span", "episode-progress");
      progress.setAttribute("aria-hidden", "true");
      const progressFill = document.createElement("span");
      progressFill.style.width = percent + "%";
      progress.appendChild(progressFill);
      progressGroup.append(progressLabel, progress);
      const action = createElement("span", "episode-action");
      action.setAttribute("aria-hidden", "true");
      action.append(createIcon("play", "icon episode-play-icon"), createIcon("headphones", "icon episode-playing-icon"));
      bottom.append(progressGroup, action);

      copy.appendChild(top);
      copy.appendChild(title);
      if (lesson.titleZh) {
        copy.appendChild(titleZh);
      }
      copy.appendChild(bottom);
      button.appendChild(cover);
      button.appendChild(copy);
      fragment.appendChild(button);
    });

    dom.episodeList.replaceChildren(fragment);
    updateLoadMoreUi();
  }

  function setView(view, options) {
    const opts = Object.assign({ focus: true, restoreScroll: true }, options || {});
    const next = view === "focus" || view === "library" ? view : "browse";
    if (next === "focus" && !currentLesson()) {
      return;
    }
    const changed = state.view !== next;
    if (changed && state.view !== "focus") {
      state.scrollPositions[state.view] = window.scrollY || window.pageYOffset || 0;
    }
    if (next === "focus" && changed) {
      state.returnView = state.view;
    }
    state.view = next;
    dom.browseView.hidden = next !== "browse";
    dom.libraryView.hidden = next !== "library";
    dom.focusView.hidden = next !== "focus";
    dom.backToBrowseLabel.textContent = state.returnView === "library" ? "返回音频列表" : "返回首页";
    document.body.dataset.view = next;

    if (changed) {
      jumpTo(next === "focus" || !opts.restoreScroll ? 0 : state.scrollPositions[next]);
    }
    if (opts.focus) {
      const heading = next === "focus" ? dom.heroTitle
        : next === "library" ? dom.libraryPageHeading : dom.browseHeading;
      heading.focus({ preventScroll: true });
    }
  }

  // View switches jump instead of using the page's smooth scroll behaviour.
  function jumpTo(top) {
    try {
      window.scrollTo({ top: top, behavior: "instant" });
    } catch (error) {
      window.scrollTo(0, top);
    }
  }

  function enterFocus(options) {
    setView("focus", options);
  }

  function returnToBrowse(options) {
    if (state.practiceOpen) {
      closePractice({ pauseAudio: false, focus: false, silent: true });
    }
    setView(state.returnView, options);
  }

  function handleFeaturedPlay() {
    if (audio.paused) {
      togglePlay();
    }
    enterFocus();
  }

  async function shuffleFeatured() {
    if (state.lessonTotal < 2) {
      showToast("只有一期素材");
      return;
    }
    await ensureAllLessons();
    if (state.lessons.length < 2) {
      showToast("只有一期素材");
      return;
    }
    const index = window.CadenceLibrary.pickRandomIndex(
      state.lessons.length,
      state.currentIndex
    );
    const lesson = await selectLesson(index, { autoplay: false, scrollToTop: false });
    if (lesson) {
      writeStored(STORAGE_KEYS.lastFeatured, lesson.id);
    }
    if (state.view === "focus") {
      returnToBrowse({ focus: false });
    }
    showToast("换了一期");
  }

  function getSavedPercent(lesson) {
    const saved = state.progress[lesson.id];
    if (!saved) {
      return 0;
    }
    if (saved.completed) {
      return 100;
    }
    const duration = Number(saved.duration) || Number(lesson.duration) || 0;
    if (!duration) {
      return 0;
    }
    return clamp((Number(saved.position) || 0) / duration * 100, 0, 100, 0);
  }

  function getCurrentPercent() {
    const lesson = currentLesson();
    if (!state.duration) {
      return lesson ? getSavedPercent(lesson) : 0;
    }
    if (state.loadingAudio) {
      if (state.pendingSeek !== null) {
        return clamp(state.pendingSeek / state.duration * 100, 0, 100, 0);
      }
      return lesson ? getSavedPercent(lesson) : 0;
    }
    return clamp((audio.currentTime || 0) / state.duration * 100, 0, 100, 0);
  }

  function updateHeroProgress() {
    const percent = getCurrentPercent();
    const lesson = currentLesson();
    const saved = lesson ? state.progress[lesson.id] : null;
    const shownPercent = percent > 0.1
      ? percent
      : (saved && saved.completed ? 100 : 0);
    dom.heroProgressFill.style.width = Math.min(100, shownPercent) + "%";

    if (shownPercent >= 99.5) {
      dom.heroProgressText.textContent = "已完成";
    } else if (shownPercent > 0) {
      dom.heroProgressText.textContent = Math.round(shownPercent) + "%";
    } else {
      dom.heroProgressText.textContent = "尚未开始";
    }

    const activeEpisode = dom.episodeList.querySelector(".episode.is-active");
    if (activeEpisode) {
      const fill = activeEpisode.querySelector(".episode-progress span");
      if (fill) {
        fill.style.width = clamp(shownPercent, 0, 100, 0) + "%";
      }
      const progressLabel = activeEpisode.querySelector(".episode-progress-label");
      if (progressLabel) {
        progressLabel.textContent = window.CadenceLibrary.formatListeningProgress(shownPercent);
      }
    }
  }

  /* -------------------------------------------------------
     Transcript state and scrolling
     ------------------------------------------------------- */

  function findActiveLine(time, lesson) {
    const activeLesson = lesson || currentLesson();
    if (!activeLesson || !activeLesson.transcript.length) {
      return 0;
    }
    let result = 0;
    activeLesson.transcript.forEach((line, index) => {
      if (time >= Number(line.start) - 0.03) {
        result = index;
      }
    });
    return result;
  }

  function seekAudioTo(value) {
    const duration = state.duration || 0;
    const target = duration
      ? clamp(value, 0, duration, 0)
      : Math.max(0, Number(value) || 0);
    if (!state.loadingAudio && audio.readyState >= 1) {
      try {
        audio.currentTime = target;
        state.pendingSeek = null;
        state.pendingResumeToast = false;
        return true;
      } catch (error) {
        /* Fall through and apply the seek once metadata is available. */
      }
    }
    state.pendingSeek = target;
    state.pendingResumeToast = false;
    return false;
  }

  function setLoopTarget(index) {
    if (!state.loopSentence) {
      return;
    }
    const lesson = currentLesson();
    if (!lesson || !lesson.transcript.length) {
      return;
    }
    state.loopLineIndex = clamp(index, 0, lesson.transcript.length - 1, 0);
    updateLoopUi();
  }

  function setActiveLine(index, options) {
    const opts = Object.assign({ scroll: true, fromPractice: false }, options || {});
    const lesson = currentLesson();
    if (!lesson || !lesson.transcript.length) {
      return;
    }
    const nextIndex = clamp(index, 0, lesson.transcript.length - 1, 0);
    const changed = state.activeLineIndex !== nextIndex;
    state.activeLineIndex = nextIndex;

    const rows = dom.transcriptList.querySelectorAll(".sentence");
    rows.forEach((row, rowIndex) => {
      const active = rowIndex === nextIndex;
      row.classList.toggle("is-active", active);
      const main = row.querySelector(".sentence-main");
      if (main) {
        if (active) {
          main.setAttribute("aria-current", "true");
        } else {
          main.removeAttribute("aria-current");
        }
      }
    });

    if (state.practiceOpen && changed && !opts.fromPractice) {
      state.practiceIndex = nextIndex;
      setLoopTarget(nextIndex);
      renderPractice();
    }

    if (opts.scroll !== false) {
      scrollActiveLineIntoView(nextIndex);
    }
  }

  function scrollActiveLineIntoView(index) {
    if (!state.follow || state.view !== "focus" || dom.transcriptView.hidden) {
      return;
    }
    const row = dom.transcriptList.querySelector('.sentence[data-index="' + index + '"]');
    if (!row) {
      return;
    }
    const list = dom.transcriptList;
    const rowRect = row.getBoundingClientRect();

    if (list.scrollHeight <= list.clientHeight + 4) {
      // At the mobile breakpoint the transcript is fully expanded, so follow
      // the current line by scrolling the page instead of an inner list.
      if (state.practiceOpen) {
        return;
      }
      const playerTop = dom.player.getBoundingClientRect().top;
      const safeTop = 16;
      const safeBottom = Math.min(window.innerHeight, playerTop) - 24;
      if (rowRect.top >= safeTop && rowRect.bottom <= safeBottom) {
        return;
      }
      const targetTop = window.scrollY + rowRect.top - Math.max(92, safeTop + 76);
      window.scrollTo({ top: Math.max(0, targetTop), behavior: scrollBehavior() });
      return;
    }

    const listRect = list.getBoundingClientRect();
    const safeTop = listRect.top + 16;
    const safeBottom = listRect.bottom - 16;
    if (rowRect.top >= safeTop && rowRect.bottom <= safeBottom) {
      return;
    }
    const target = list.scrollTop + (rowRect.top - listRect.top) - (list.clientHeight - rowRect.height) / 2;
    list.scrollTo({ top: Math.max(0, target), behavior: scrollBehavior() });
  }

  /* -------------------------------------------------------
     Playback
     ------------------------------------------------------- */

  function togglePlay() {
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    state.segmentEnd = null;
    if (audio.paused) {
      if (Number.isFinite(audio.duration) && audio.currentTime >= audio.duration - 0.15) {
        const loopLine = state.loopSentence && state.loopLineIndex !== null
          ? lesson.transcript[state.loopLineIndex]
          : null;
        seekAudioTo(loopLine ? Math.max(0, Number(loopLine.start) + 0.01) : 0);
      }
      safePlay();
    } else {
      audio.pause();
    }
  }

  function safePlay() {
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    recordHistory(lesson.id);
    if (!recordingAudio.paused) {
      stopRecordingPlayback();
    }
    const playPromise = audio.play();
    if (playPromise && typeof playPromise.catch === "function") {
      playPromise.catch((error) => {
        if (error && error.name === "AbortError") {
          return;
        }
        state.isPlaying = false;
        updatePlayState();
        showToast("浏览器阻止了播放，请再点一次播放按钮。");
      });
    }
  }

  function playFromLine(index) {
    const lesson = currentLesson();
    if (!lesson || !lesson.transcript[index]) {
      return;
    }
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    stopRecordingPlayback();
    if (state.practiceOpen) {
      state.practiceIndex = index;
    }
    setLoopTarget(index);
    state.segmentEnd = null;
    const line = lesson.transcript[index];
    seekAudioTo(Math.max(0, Number(line.start) + 0.01));
    setActiveLine(index, { fromPractice: true, scroll: true });
    if (state.practiceOpen) {
      renderPractice();
      refreshRecordStatus();
    }
    safePlay();
  }

  function seekBy(seconds) {
    if (!state.duration) {
      return;
    }
    state.segmentEnd = null;
    const target = clamp((audio.currentTime || 0) + seconds, 0, Math.max(0, state.duration - 0.02), 0);
    seekAudioTo(target);
    setLoopTarget(findActiveLine(target));
    setActiveLine(findActiveLine(target), { scroll: true, fromPractice: false });
    updateTimeline();
    updateHeroProgress();
  }

  async function stepLesson(direction) {
    if (state.lessons.length < 2 && !state.hasMoreLessons) {
      return;
    }
    const shouldKeepPlaying = state.isPlaying;
    const index = state.currentIndex >= 0 ? state.currentIndex : 0;
    let next = index + direction;
    if (next >= state.lessons.length) {
      if (state.hasMoreLessons) {
        await loadMoreLessons();
      }
      if (next >= state.lessons.length) {
        next = 0;
      }
    } else if (next < 0) {
      next = state.lessons.length - 1;
    }
    selectLesson(next, {
      autoplay: shouldKeepPlaying,
      scrollToTop: false
    });
  }

  function updateTimeline() {
    const duration = state.duration || Number(currentLesson() && currentLesson().duration) || 0;
    const pendingTime = state.loadingAudio && state.pendingSeek !== null ? state.pendingSeek : null;
    const time = pendingTime !== null ? pendingTime : (Number(audio.currentTime) || 0);
    const percent = duration ? clamp(time / duration * 100, 0, 100, 0) : 0;

    dom.progressFill.style.width = percent + "%";
    dom.progressThumb.style.left = percent + "%";
    dom.currentTime.textContent = formatTime(time);
    dom.durationTime.textContent = formatTime(duration);

    if (!state.isSeeking) {
      dom.seekSlider.value = String(Math.min(time, duration || time));
    }
    dom.seekSlider.setAttribute("aria-valuetext", formatTime(time) + " / " + formatTime(duration));

    if (audio.buffered && audio.buffered.length && duration) {
      try {
        const bufferedEnd = audio.buffered.end(audio.buffered.length - 1);
        dom.bufferBar.style.width = clamp(bufferedEnd / duration * 100, 0, 100, 0) + "%";
      } catch (error) {
        dom.bufferBar.style.width = "0%";
      }
    }
  }

  function handleTimeUpdate() {
    if (state.loadingAudio) {
      return;
    }
    updateTimeline();
    if (!state.duration) {
      return;
    }

    if (state.segmentEnd !== null && audio.currentTime >= state.segmentEnd - 0.03) {
      const segmentIndex = state.practiceIndex;
      const lesson = currentLesson();
      const segmentLine = lesson && lesson.transcript[segmentIndex];
      state.segmentEnd = null;
      audio.pause();
      try {
        const stopAt = segmentLine
          ? Math.max(Number(segmentLine.start) || 0,
            Math.min(state.duration - 0.02, Number(segmentLine.end) - 0.06))
          : Math.min(state.duration, audio.currentTime);
        audio.currentTime = stopAt;
      } catch (error) {
        /* The browser may briefly reject a seek while ending playback. */
      }
      setActiveLine(segmentIndex, { fromPractice: true, scroll: true });
      updateTimeline();
      updateHeroProgress();
      refreshRecordStatus();
      return;
    }

    if (state.loopSentence && state.loopLineIndex !== null) {
      const lesson = currentLesson();
      const loopLine = lesson && lesson.transcript[state.loopLineIndex];
      if (loopLine && audio.currentTime >= Number(loopLine.end) - 0.02) {
        try {
          audio.currentTime = Math.max(0, Number(loopLine.start) + 0.01);
        } catch (error) {
          /* Ignore transient seek failures. */
        }
      }
    }

    const activeIndex = findActiveLine(audio.currentTime);
    setActiveLine(activeIndex, { scroll: true });
    updateHeroProgress();

    if (Date.now() - state.lastProgressSave > 900) {
      state.lastProgressSave = Date.now();
      saveProgress();
    }

    updateMediaPositionState();
  }

  function handleEnded() {
    if (state.loopSentence && state.loopLineIndex !== null) {
      const lesson = currentLesson();
      const line = lesson && lesson.transcript[state.loopLineIndex];
      if (line) {
        try {
          audio.currentTime = Math.max(0, Number(line.start) + 0.01);
        } catch (error) {
          return;
        }
        safePlay();
        return;
      }
    }

    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    const duration = state.duration || Number(lesson.duration) || 0;
    state.progress[lesson.id] = {
      position: duration,
      duration: duration,
      completed: true,
      updatedAt: Date.now()
    };
    writeStored(STORAGE_KEYS.progress, state.progress);
    updatePlayState();
    updateTimeline();
    updateHeroProgress();
    showToast("本期精听完成 · 可以试试跟读练习。");
  }

  function updatePlayState() {
    const playing = !audio.paused && !audio.ended && Boolean(currentLesson());
    state.isPlaying = playing;

    document.querySelectorAll(".js-play-icon").forEach((icon) => {
      icon.classList.toggle("is-hidden", playing);
    });
    document.querySelectorAll(".js-pause-icon").forEach((icon) => {
      icon.classList.toggle("is-hidden", !playing);
    });

    dom.player.classList.toggle("is-playing", playing);
    dom.playBtn.setAttribute("aria-label", playing ? "暂停" : "播放");

    const label = dom.heroPlayBtn.querySelector(".js-play-label");
    if (label) {
      const nearlyDone = state.duration > 0 && audio.currentTime >= state.duration - 0.3;
      if (playing) {
        label.textContent = "继续收听";
      } else if (nearlyDone) {
        label.textContent = "重新精听";
      } else if (audio.currentTime > 0.5) {
        label.textContent = "继续收听";
      } else {
        label.textContent = "开始精听";
      }
      dom.heroPlayBtn.setAttribute("aria-label", label.textContent);
    }

    dom.episodeList.querySelectorAll(".episode").forEach((episode) => {
      const active = Number(episode.dataset.index) === state.currentIndex;
      episode.classList.toggle("is-playing", active && playing);
    });

    if ("mediaSession" in navigator) {
      navigator.mediaSession.playbackState = playing ? "playing" : "paused";
    }
    updateMediaPositionState();
  }

  function saveProgress() {
    const lesson = currentLesson();
    if (state.loadingAudio || !lesson || !Number.isFinite(audio.currentTime)) {
      return;
    }
    const duration = state.duration || Number(lesson.duration) || 0;
    if (!duration) {
      return;
    }
    const position = clamp(audio.currentTime, 0, duration, 0);
    if (position <= 0 && !state.progress[lesson.id]) {
      return;
    }
    const previous = state.progress[lesson.id] || {};
    const completed = Boolean(previous.completed) || position >= duration - 0.8;
    state.progress[lesson.id] = {
      position: position,
      duration: duration,
      completed: completed,
      updatedAt: Date.now()
    };
    writeStored(STORAGE_KEYS.progress, state.progress);
    updateHeroProgress();
  }

  /* -------------------------------------------------------
     Timeline and volume
     ------------------------------------------------------- */

  function handleSeekInput(event) {
    if (!state.duration) {
      return;
    }
    state.isSeeking = true;
    dom.scrubber.classList.add("is-scrubbing");
    state.segmentEnd = null;
    const target = clamp(Number(event.target.value), 0, state.duration, 0);
    seekAudioTo(target);
    setLoopTarget(findActiveLine(target));
    setActiveLine(findActiveLine(target), { scroll: false, fromPractice: false });
    dom.currentTime.textContent = formatTime(target);
    dom.progressFill.style.width = (state.duration ? target / state.duration * 100 : 0) + "%";
    dom.progressThumb.style.left = (state.duration ? target / state.duration * 100 : 0) + "%";
    dom.seekSlider.setAttribute("aria-valuetext", formatTime(target) + " / " + formatTime(state.duration));
  }

  function finishSeeking() {
    if (!state.isSeeking) {
      return;
    }
    state.isSeeking = false;
    dom.scrubber.classList.remove("is-scrubbing");
    dom.seekTooltip.hidden = true;
    setActiveLine(findActiveLine(audio.currentTime), { scroll: true });
    updateTimeline();
    updateHeroProgress();
    saveProgress();
  }

  function showSeekPreview(event) {
    if (!state.duration) {
      return;
    }
    const rect = dom.scrubber.getBoundingClientRect();
    if (!rect.width) {
      return;
    }
    const ratio = clamp((event.clientX - rect.left) / rect.width, 0, 1, 0);
    dom.seekTooltip.textContent = formatTime(ratio * state.duration);
    dom.seekTooltip.style.left = ratio * 100 + "%";
    dom.seekTooltip.hidden = false;
  }

  function hideSeekPreview() {
    if (!state.isSeeking) {
      dom.seekTooltip.hidden = true;
    }
  }

  function setVolume(value, options) {
    const opts = Object.assign({ persist: true }, options || {});
    const next = clamp(value, 0, 1, state.volume);
    state.volume = next;
    audio.volume = next;
    if (next > 0) {
      audio.muted = false;
      state.muted = false;
      state.lastVolume = next;
    } else {
      audio.muted = true;
      state.muted = true;
    }
    updateVolumeUi();
    if (opts.persist) {
      persistSettings();
    }
  }

  function updateVolumeUi() {
    dom.volumeSlider.value = String(state.volume);
    const silent = audio.muted || audio.volume === 0;
    document.querySelectorAll(".js-volume-on").forEach((icon) => icon.classList.toggle("is-hidden", silent));
    document.querySelectorAll(".js-volume-off").forEach((icon) => icon.classList.toggle("is-hidden", !silent));
    dom.muteBtn.setAttribute("aria-label", silent ? "取消静音" : "静音");
    dom.muteBtn.title = silent ? "取消静音" : "静音";
  }

  function toggleMute() {
    if (audio.muted || audio.volume === 0) {
      audio.muted = false;
      state.muted = false;
      if (audio.volume === 0) {
        audio.volume = state.lastVolume || 0.6;
        state.volume = audio.volume;
      }
      showToast("已取消静音");
    } else {
      state.lastVolume = audio.volume;
      audio.muted = true;
      state.muted = true;
      showToast("已静音");
    }
    updateVolumeUi();
    persistSettings();
  }

  function shiftVolume(delta) {
    const next = clamp(audio.volume + delta, 0, 1, audio.volume);
    setVolume(next, { persist: true });
  }

  /* -------------------------------------------------------
     Speed and loop
     ------------------------------------------------------- */

  function setSpeed(speed, options) {
    const opts = Object.assign({ announce: true }, options || {});
    const next = SPEEDS.indexOf(Number(speed)) >= 0 ? Number(speed) : 1;
    state.speed = next;
    audio.playbackRate = next;
    audio.preservesPitch = true;
    updateSpeedUi();
    persistSettings();
    if (opts.announce) {
      showToast("播放速度 " + formatSpeed(next));
    }
  }

  function updateSpeedUi() {
    dom.speedLabel.textContent = formatSpeed(state.speed);
    dom.speedBtn.title = "播放速度 " + formatSpeed(state.speed);
    dom.speedMenu.querySelectorAll("[data-speed]").forEach((button) => {
      const active = Number(button.dataset.speed) === state.speed;
      button.classList.toggle("is-current", active);
      button.setAttribute("aria-checked", String(active));
    });
  }

  function openSpeedMenu() {
    dom.speedMenu.hidden = false;
    dom.speedBtn.setAttribute("aria-expanded", "true");
    const currentButton = dom.speedMenu.querySelector("[data-speed].is-current");
    if (currentButton) {
      currentButton.focus({ preventScroll: true });
    }
  }

  function closeSpeedMenu() {
    if (dom.speedMenu.hidden) {
      return;
    }
    dom.speedMenu.hidden = true;
    dom.speedBtn.setAttribute("aria-expanded", "false");
  }

  function setLoopSentence(enabled, lineIndex) {
    state.loopSentence = Boolean(enabled);
    if (state.loopSentence) {
      const lesson = currentLesson();
      const fallback = state.activeLineIndex >= 0 ? state.activeLineIndex : 0;
      const index = Number.isInteger(lineIndex) ? lineIndex : fallback;
      state.loopLineIndex = lesson ? clamp(index, 0, lesson.transcript.length - 1, 0) : null;
    } else {
      state.loopLineIndex = null;
    }
    updateLoopUi();
    if (state.loopSentence) {
      const line = currentLesson() && currentLesson().transcript[state.loopLineIndex];
      showToast("单句循环 · " + (line ? "第 " + (state.loopLineIndex + 1) + " 句" : "当前句"));
    } else {
      showToast("已关闭单句循环");
    }
  }

  function updateLoopUi() {
    dom.loopBtn.classList.toggle("is-active", state.loopSentence);
    dom.loopBtn.setAttribute("aria-pressed", String(state.loopSentence));
    dom.loopBtn.title = state.loopSentence ? "取消单句循环 (R)" : "单句循环 (R)";

    dom.transcriptList.querySelectorAll('[data-action="loop"]').forEach((button) => {
      const row = button.closest(".sentence");
      const active = state.loopSentence && row && Number(row.dataset.index) === state.loopLineIndex;
      button.classList.toggle("is-looping", Boolean(active));
    });
  }

  /* -------------------------------------------------------
     Shadowing mode
     ------------------------------------------------------- */

  function openPractice(index, opener) {
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    closeSpeedMenu();
    stopRecordingPlayback();

    if (!state.practiceOpen) {
      state.practiceOpen = true;
      state.practiceOpener = opener || document.activeElement;
      dom.practicePanel.hidden = false;
    }

    const target = Number.isInteger(index)
      ? index
      : clamp(state.activeLineIndex, 0, lesson.transcript.length - 1, 0);

    goToPracticeLine(target, { pauseAudio: true, scroll: false });
    dom.practicePanel.scrollIntoView({ block: "center", behavior: scrollBehavior() });
  }

  function closePractice(options) {
    const opts = Object.assign({ pauseAudio: true, focus: true, silent: false }, options || {});
    if (!state.practiceOpen) {
      return;
    }

    state.micRequestId += 1;
    state.micPending = false;
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    stopRecordingPlayback();
    state.segmentEnd = null;
    if (opts.pauseAudio) {
      audio.pause();
    }
    state.practiceOpen = false;
    dom.practicePanel.hidden = true;
    syncPracticeRecordingUi();
    if (opts.focus && state.practiceOpener && document.contains(state.practiceOpener)) {
      state.practiceOpener.focus({ preventScroll: true });
    }
    state.practiceOpener = null;
  }

  function goToPracticeLine(index, options) {
    const opts = Object.assign({ pauseAudio: true, scroll: true }, options || {});
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    if (state.micPending) {
      state.micRequestId += 1;
      state.micPending = false;
    }
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    stopRecordingPlayback();
    if (opts.pauseAudio) {
      audio.pause();
    }
    state.segmentEnd = null;
    state.practiceIndex = clamp(index, 0, lesson.transcript.length - 1, 0);
    setLoopTarget(state.practiceIndex);
    const line = lesson.transcript[state.practiceIndex];
    if (line) {
      seekAudioTo(Math.max(0, Number(line.start) + 0.01));
    }
    setActiveLine(state.practiceIndex, { fromPractice: true, scroll: opts.scroll });
    renderPractice();
    refreshRecordStatus();
  }

  function renderPractice() {
    if (!state.practiceOpen) {
      return;
    }
    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    const line = lesson.transcript[state.practiceIndex] || lesson.transcript[0];
    if (!line) {
      return;
    }

    dom.practiceCounter.textContent = "第 " + (state.practiceIndex + 1) + " / " + lesson.transcript.length + " 句";
    dom.practiceEn.textContent = line.text;
    dom.practiceZh.textContent = line.translation || "";
    dom.prevSentenceBtn.disabled = state.practiceIndex <= 0;
    dom.nextSentenceBtn.disabled = state.practiceIndex >= lesson.transcript.length - 1;
    syncPracticeRecordingUi();
    refreshRecordStatus();
  }

  function syncPracticeRecordingUi() {
    if (!state.practiceOpen) {
      return;
    }
    const recording = getRecording(state.practiceIndex);
    const key = currentRecordingKey();
    const isPlayback = state.recordingPlaybackKey === key && !recordingAudio.paused;

    dom.recordBtn.disabled = state.micPending;
    dom.recordBtn.classList.toggle("is-recording", state.isRecording);
    dom.recordBtn.setAttribute("aria-pressed", String(state.isRecording));
    dom.recordBtn.setAttribute("aria-label", state.isRecording ? "停止录音" : "开始录音");
    dom.recordBtn.querySelector(".js-record-icon").classList.toggle("is-hidden", state.isRecording);
    dom.recordBtn.querySelector(".js-stop-icon").classList.toggle("is-hidden", !state.isRecording);
    dom.recordBtn.querySelector(".js-record-label").textContent = state.micPending
      ? "正在授权…"
      : (state.isRecording ? "停止录音" : "开始录音");
    dom.recordVisual.classList.toggle("is-recording", state.isRecording);

    dom.playRecordingBtn.disabled = !recording || state.isRecording || state.micPending;
    dom.playRecordingBtn.querySelector("span").textContent = isPlayback ? "暂停回放" : "回放录音";
  }

  function refreshRecordStatus() {
    if (!state.practiceOpen || state.isRecording || state.micPending) {
      return;
    }
    const recording = getRecording(state.practiceIndex);
    const key = currentRecordingKey();
    if (state.recordingPlaybackKey === key && !recordingAudio.paused) {
      dom.recordStatus.textContent = "正在回放你的录音…";
      return;
    }
    if (recording) {
      dom.recordStatus.textContent = "已录制 " + formatTime(recording.duration) + " · 录音仅保存在当前页面。";
    } else {
      dom.recordStatus.textContent = "录音仅保存在当前页面，不会上传。";
    }
  }

  function listenToSentence() {
    const lesson = currentLesson();
    if (!lesson || !state.practiceOpen) {
      return;
    }
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    stopRecordingPlayback();
    const line = lesson.transcript[state.practiceIndex];
    if (!line) {
      return;
    }
    state.segmentEnd = Math.min(Number(line.end) || 0, state.duration || Number(line.end) || 0);
    seekAudioTo(Math.max(0, Number(line.start) + 0.01));
    setActiveLine(state.practiceIndex, { fromPractice: true, scroll: true });
    safePlay();
  }

  async function startRecording() {
    if (!state.practiceOpen || state.micPending) {
      return;
    }
    if (state.isRecording) {
      stopRecording({ save: true });
      return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || typeof window.MediaRecorder === "undefined") {
      dom.recordStatus.textContent = "当前浏览器不支持录音，请使用 Chrome 或 Edge。";
      showToast("当前浏览器不支持录音。");
      return;
    }

    const lesson = currentLesson();
    if (!lesson) {
      return;
    }
    const target = { lessonId: lesson.id, index: state.practiceIndex };
    const requestId = state.micRequestId + 1;
    state.micRequestId = requestId;
    state.micPending = true;

    audio.pause();
    state.segmentEnd = null;
    stopRecordingPlayback();
    syncPracticeRecordingUi();
    dom.recordStatus.textContent = "正在请求麦克风权限…";

    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
    } catch (error) {
      if (requestId === state.micRequestId) {
        state.micPending = false;
        syncPracticeRecordingUi();
        dom.recordStatus.textContent = error && error.name === "NotAllowedError"
          ? "麦克风权限被拒绝，请在浏览器设置中允许后重试。"
          : "无法启用麦克风，请检查设备连接后重试。";
        showToast("无法启用麦克风。");
      }
      return;
    }

    if (requestId !== state.micRequestId
      || !state.practiceOpen
      || !currentLesson()
      || currentLesson().id !== target.lessonId
      || state.practiceIndex !== target.index) {
      stream.getTracks().forEach((track) => track.stop());
      if (requestId === state.micRequestId) {
        state.micPending = false;
        syncPracticeRecordingUi();
      }
      if (state.practiceOpen) {
        refreshRecordStatus();
      }
      return;
    }

    try {
      const mimeType = pickRecordingMimeType();
      const recorder = mimeType
        ? new MediaRecorder(stream, { mimeType: mimeType })
        : new MediaRecorder(stream);
      const chunks = [];
      const startTime = performance.now();

      recorder.addEventListener("dataavailable", (event) => {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data);
        }
      });
      recorder.addEventListener("error", () => {
        if (state.recorder === recorder) {
          cleanupRecorderState();
          dom.recordStatus.textContent = "录音出现问题，请再试一次。";
        }
      });
      recorder.addEventListener("stop", () => {
        stream.getTracks().forEach((track) => track.stop());
        if (recorder.__discardRecording) {
          return;
        }
        const elapsed = Math.max(0.2, (performance.now() - startTime) / 1000);
        finalizeRecording(target, chunks, recorder.mimeType || "audio/webm", elapsed);
      });

      state.recorder = recorder;
      state.recordingStream = stream;
      state.recordingTarget = target;
      state.isRecording = true;
      state.micPending = false;
      state.recordStartedAt = performance.now();
      recorder.start();

      syncPracticeRecordingUi();
      dom.recordStatus.textContent = "正在录音 · 00:00 · 再点一次停止";
      state.recordTimer = window.setInterval(() => {
        const elapsed = (performance.now() - state.recordStartedAt) / 1000;
        if (elapsed >= 30) {
          stopRecording({ save: true });
          return;
        }
        dom.recordStatus.textContent = "正在录音 · " + formatTime(elapsed) + " · 再点一次停止";
      }, 300);
    } catch (error) {
      stream.getTracks().forEach((track) => track.stop());
      state.isRecording = false;
      state.micPending = false;
      state.recorder = null;
      state.recordingStream = null;
      syncPracticeRecordingUi();
      dom.recordStatus.textContent = "无法开始录音，请稍后重试。";
    }
  }

  function pickRecordingMimeType() {
    if (!window.MediaRecorder || typeof MediaRecorder.isTypeSupported !== "function") {
      return "";
    }
    const candidates = [
      "audio/webm;codecs=opus",
      "audio/webm",
      "audio/mp4;codecs=mp4a.40.2",
      "audio/mp4"
    ];
    for (let index = 0; index < candidates.length; index += 1) {
      if (MediaRecorder.isTypeSupported(candidates[index])) {
        return candidates[index];
      }
    }
    return "";
  }

  function stopRecording(options) {
    const opts = Object.assign({ save: true, silent: false }, options || {});
    if (!state.isRecording || !state.recorder) {
      return;
    }
    window.clearInterval(state.recordTimer);
    state.recordTimer = null;

    const recorder = state.recorder;
    recorder.__discardRecording = !opts.save;
    state.isRecording = false;
    state.recorder = null;
    state.micPending = false;

    try {
      if (recorder.state !== "inactive") {
        recorder.stop();
      }
    } catch (error) {
      /* Recorder may already have stopped itself at the time limit. */
    }

    if (state.recordingStream) {
      state.recordingStream.getTracks().forEach((track) => track.stop());
      state.recordingStream = null;
    }
    syncPracticeRecordingUi();
    if (opts.save) {
      dom.recordStatus.textContent = "正在保存录音…";
    }
  }

  function cleanupRecorderState() {
    window.clearInterval(state.recordTimer);
    state.recordTimer = null;
    if (state.recordingStream) {
      state.recordingStream.getTracks().forEach((track) => track.stop());
      state.recordingStream = null;
    }
    state.recorder = null;
    state.isRecording = false;
    state.micPending = false;
    syncPracticeRecordingUi();
  }

  function finalizeRecording(target, chunks, mimeType, elapsed) {
    if (!chunks.length) {
      if (state.practiceOpen && currentLesson() && currentLesson().id === target.lessonId && state.practiceIndex === target.index) {
        dom.recordStatus.textContent = "没有录到声音，请检查麦克风后再试。";
      }
      return;
    }

    const blob = new Blob(chunks, { type: mimeType });
    if (!blob.size) {
      return;
    }
    const key = recordingKey(target.lessonId, target.index);
    const previous = state.recordings.get(key);
    if (previous && previous.url) {
      URL.revokeObjectURL(previous.url);
    }
    const url = URL.createObjectURL(blob);
    state.recordings.set(key, { url: url, duration: elapsed, createdAt: Date.now() });

    const lesson = currentLesson();
    if (lesson && lesson.id === target.lessonId) {
      const practiceAction = dom.transcriptList.querySelector(
        '.sentence[data-index="' + target.index + '"] [data-action="practice"]'
      );
      if (practiceAction) {
        practiceAction.classList.add("is-recorded");
      }
    }

    const isCurrent = state.practiceOpen
      && lesson
      && lesson.id === target.lessonId
      && state.practiceIndex === target.index;
    if (isCurrent && !state.isRecording && !state.micPending) {
      syncPracticeRecordingUi();
      refreshRecordStatus();
      dom.recordStatus.textContent = "已保存录音 " + formatTime(elapsed) + " · 可以回放或重新录制。";
    }
  }

  function stopRecordingPlayback() {
    if (!recordingAudio.paused) {
      recordingAudio.pause();
    }
    state.recordingPlaybackKey = null;
    syncPracticeRecordingUi();
    if (state.practiceOpen) {
      refreshRecordStatus();
    }
  }

  function playRecording() {
    const recording = getRecording(state.practiceIndex);
    if (!recording) {
      return;
    }
    const key = currentRecordingKey();
    if (state.recordingPlaybackKey === key && !recordingAudio.paused) {
      recordingAudio.pause();
      return;
    }

    audio.pause();
    state.segmentEnd = null;
    if (state.isRecording) {
      stopRecording({ save: true, silent: true });
    }
    state.recordingPlaybackKey = key;
    recordingAudio.src = recording.url;
    recordingAudio.currentTime = 0;
    recordingAudio.volume = Math.max(0.1, state.volume);
    const promise = recordingAudio.play();
    if (promise && promise.catch) {
      promise.catch(() => {
        dom.recordStatus.textContent = "录音回放失败，请重新录制。";
      });
    }
    dom.recordStatus.textContent = "正在回放你的录音…";
    syncPracticeRecordingUi();
  }

  /* -------------------------------------------------------
     Media Session
     ------------------------------------------------------- */

  function setupMediaSessionHandlers() {
    if (!("mediaSession" in navigator)) {
      return;
    }
    const handlers = {
      play: () => safePlay(),
      pause: () => audio.pause(),
      previoustrack: () => stepLesson(-1),
      nexttrack: () => stepLesson(1),
      seekbackward: (details) => seekBy(-(details.seekOffset || 10)),
      seekforward: (details) => seekBy(details.seekOffset || 10),
      seekto: (details) => {
        if (Number.isFinite(details.seekTime) && state.duration) {
          const target = clamp(details.seekTime, 0, state.duration, 0);
          seekAudioTo(target);
          setLoopTarget(findActiveLine(target));
          setActiveLine(findActiveLine(target), { scroll: true });
          updateTimeline();
        }
      }
    };
    Object.keys(handlers).forEach((action) => {
      try {
        navigator.mediaSession.setActionHandler(action, handlers[action]);
      } catch (error) {
        /* Not every browser supports every media action. */
      }
    });
  }

  function updateMediaMetadata() {
    const lesson = currentLesson();
    if (!lesson || !("mediaSession" in navigator) || typeof window.MediaMetadata === "undefined") {
      return;
    }
    try {
      const artworkUrl = new URL(lesson.cover || "/images/cover-default.svg", window.location.origin).toString();
      navigator.mediaSession.metadata = new MediaMetadata({
        title: lesson.title,
        artist: "Cadence 英语精听",
        album: lesson.titleZh || "",
        artwork: [{ src: artworkUrl, sizes: "760x640", type: "image/svg+xml" }]
      });
    } catch (error) {
      /* Metadata is a progressive enhancement. */
    }
  }

  function updateMediaPositionState() {
    if (!("mediaSession" in navigator) || typeof navigator.mediaSession.setPositionState !== "function") {
      return;
    }
    if (!state.duration || !Number.isFinite(state.duration) || state.duration <= 0) {
      return;
    }
    try {
      navigator.mediaSession.setPositionState({
        duration: state.duration,
        playbackRate: audio.playbackRate || 1,
        position: clamp(audio.currentTime, 0, state.duration, 0)
      });
    } catch (error) {
      /* Position state can be rejected while media is settling. */
    }
  }

  /* -------------------------------------------------------
     History drawer
     ------------------------------------------------------- */

  function hasListened(lessonId) {
    return state.history.indexOf(lessonId) >= 0;
  }

  function recordHistory(lessonId) {
    if (!lessonId) {
      return;
    }
    const next = state.history.filter((id) => id !== lessonId);
    next.unshift(lessonId);
    state.history = next.slice(0, HISTORY_LIMIT);
    writeStored(STORAGE_KEYS.history, state.history);
    syncListenedMarks();
    if (dom.historyDialog.open) {
      renderHistory();
    }
  }

  function syncListenedMarks() {
    dom.episodeList.querySelectorAll(".episode").forEach((episode) => {
      const lesson = state.lessons[Number(episode.dataset.index)];
      episode.classList.toggle("is-listened", Boolean(lesson) && hasListened(lesson.id));
    });
  }

  function renderHistory() {
    const lessonsById = new Map(state.lessons.map((lesson) => [lesson.id, lesson]));
    state.lessonDetails.forEach((lesson) => {
      if (!lessonsById.has(lesson.id)) {
        lessonsById.set(lesson.id, lesson);
      }
    });
    const entries = state.history
      .map((id) => lessonsById.get(id))
      .filter(Boolean);

    dom.historyCount.textContent = entries.length + " 期";
    dom.clearHistoryBtn.hidden = Boolean(account.user);
    dom.clearHistoryBtn.disabled = state.history.length === 0;
    dom.historyList.replaceChildren();

    if (!entries.length) {
      const empty = createElement("div", "history-empty");
      empty.appendChild(createElement("strong", "", "还没有听过的内容"));
      empty.appendChild(createElement("p", "", "打开任意一期课程或开始播放后，它会出现在这里，方便随时回来重听。"));
      dom.historyList.appendChild(empty);
      return;
    }

    const fragment = document.createDocumentFragment();
    entries.forEach((lesson) => {
      const index = state.lessons.findIndex((item) => item.id === lesson.id);
      const item = createElement("button", "history-item"
        + (lesson.id === state.currentLessonId ? " is-active" : ""));
      item.type = "button";
      item.dataset.id = lesson.id;
      item.setAttribute("aria-label", "重听：" + lesson.title);

      const cover = createElement("span", "history-cover");
      const image = document.createElement("img");
      image.src = lesson.cover || "/images/cover-default.svg";
      image.alt = "";
      image.loading = "lazy";
      image.decoding = "async";
      cover.appendChild(image);
      cover.appendChild(createElement("span", "history-number",
        lesson.number || String(Math.max(0, index) + 1).padStart(2, "0")));

      const copy = createElement("span", "history-copy");
      const top = createElement("span", "history-topline");
      top.appendChild(createElement("span", "history-category", lesson.categoryZh || lesson.category || ""));
      top.appendChild(createElement("span", "history-duration", formatTime(Number(lesson.duration) || 0)));
      copy.appendChild(top);
      copy.appendChild(createElement("span", "history-title", lesson.title));
      if (lesson.titleZh) {
        copy.appendChild(createElement("span", "history-zh", lesson.titleZh));
      }

      item.appendChild(cover);
      item.appendChild(copy);
      item.appendChild(createIcon("play", "icon history-play"));
      fragment.appendChild(item);
    });
    dom.historyList.appendChild(fragment);
  }

  function openHistory() {
    closeSpeedMenu();
    renderHistory();
    if (typeof dom.historyDialog.showModal === "function") {
      if (!dom.historyDialog.open) {
        dom.historyDialog.showModal();
      }
    } else {
      dom.historyDialog.setAttribute("open", "");
    }
    document.body.classList.add("is-dialog-open");
    dom.closeHistoryBtn.focus({ preventScroll: true });
  }

  function closeHistory() {
    if (typeof dom.historyDialog.close === "function" && dom.historyDialog.open) {
      dom.historyDialog.close();
    } else {
      dom.historyDialog.removeAttribute("open");
    }
    document.body.classList.remove("is-dialog-open");
  }

  function clearHistory() {
    state.history = [];
    writeStored(STORAGE_KEYS.history, []);
    renderHistory();
    syncListenedMarks();
    showToast("历史记录已清空");
  }

  async function playHistoryEntry(lessonId) {
    if (!lessonId) {
      return;
    }
    closeHistory();
    const index = state.lessons.findIndex((lesson) => lesson.id === lessonId);
    if (index >= 0) {
      selectLesson(index, { autoplay: true, enterFocus: true, scrollToTop: false });
      return;
    }

    let lesson = state.lessonDetails.get(lessonId);
    if (!lesson) {
      try {
        lesson = await fetchLessonDetail(lessonId);
      } catch (error) {
        console.error(error);
      }
    }
    if (!isUsableLesson(lesson)) {
      showToast("课程加载失败，请稍后重试。");
      return;
    }
    openLesson(lesson, { autoplay: true, enterFocus: true, scrollToTop: false });
  }

  /* -------------------------------------------------------
     Shortcuts dialog, toggles, events
     ------------------------------------------------------- */

  function openShortcuts() {
    closeSpeedMenu();
    if (typeof dom.shortcutsDialog.showModal === "function") {
      if (!dom.shortcutsDialog.open) {
        dom.shortcutsDialog.showModal();
      }
    } else {
      dom.shortcutsDialog.setAttribute("open", "");
    }
    document.body.classList.add("is-dialog-open");
    dom.closeShortcutsBtn.focus({ preventScroll: true });
  }

  function closeShortcuts() {
    if (typeof dom.shortcutsDialog.close === "function" && dom.shortcutsDialog.open) {
      dom.shortcutsDialog.close();
    } else {
      dom.shortcutsDialog.removeAttribute("open");
    }
    document.body.classList.remove("is-dialog-open");
  }

  function toggleTranslation() {
    state.translation = !state.translation;
    applyToggles();
    persistSettings();
  }

  function toggleFollow() {
    state.follow = !state.follow;
    applyToggles();
    persistSettings();
    if (state.follow) {
      scrollActiveLineIntoView(state.activeLineIndex);
    }
  }

  function bindEvents() {
    audio.addEventListener("loadedmetadata", () => {
      const lesson = currentLesson();
      if (!lesson) {
        return;
      }
      state.loadingAudio = false;
      const actualDuration = Number(audio.duration);
      state.duration = Number.isFinite(actualDuration) && actualDuration > 0
        ? actualDuration
        : (Number(lesson.duration) || 0);
      dom.durationTime.textContent = formatTime(state.duration);
      dom.durationMeta.textContent = formatTime(state.duration);
      dom.seekSlider.max = String(state.duration);
      dom.seekSlider.disabled = !state.duration;

      if (state.pendingSeek !== null) {
        const resumeAt = clamp(state.pendingSeek, 0, Math.max(0, state.duration - 0.2), 0);
        try {
          audio.currentTime = resumeAt;
        } catch (error) {
          /* Some browsers reject seeks until data is ready. */
        }
        if (state.pendingResumeToast && resumeAt > 4 && !state.resumeToasted.has(lesson.id)) {
          state.resumeToasted.add(lesson.id);
          showToast("已从上次位置 " + formatTime(resumeAt) + " 继续");
        }
        state.pendingSeek = null;
        state.pendingResumeToast = false;
      }

      const activeIndex = findActiveLine(audio.currentTime);
      setActiveLine(activeIndex, { scroll: false, fromPractice: true });
      setLoopTarget(activeIndex);
      updateTimeline();
      updateHeroProgress();
      updatePlayState();
      updateMediaMetadata();

      if (state.autoPlayOnMetadata) {
        state.autoPlayOnMetadata = false;
        safePlay();
      }
    });

    audio.addEventListener("timeupdate", handleTimeUpdate);
    audio.addEventListener("play", updatePlayState);
    audio.addEventListener("pause", () => {
      updatePlayState();
      saveProgress();
    });
    audio.addEventListener("ended", handleEnded);
    audio.addEventListener("progress", () => {
      if (!state.duration) {
        return;
      }
      if (audio.buffered && audio.buffered.length) {
        try {
          const end = audio.buffered.end(audio.buffered.length - 1);
          dom.bufferBar.style.width = clamp(end / state.duration * 100, 0, 100, 0) + "%";
        } catch (error) {
          dom.bufferBar.style.width = "0%";
        }
      }
    });
    audio.addEventListener("error", () => {
      state.loadingAudio = false;
      if (currentLesson()) {
        showToast("音频加载失败，请检查文件是否存在。");
      }
    });

    recordingAudio.addEventListener("ended", () => {
      state.recordingPlaybackKey = null;
      syncPracticeRecordingUi();
      refreshRecordStatus();
    });
    recordingAudio.addEventListener("pause", () => {
      state.recordingPlaybackKey = null;
      syncPracticeRecordingUi();
      refreshRecordStatus();
    });

    dom.heroPlayBtn.addEventListener("click", handleFeaturedPlay);
    dom.playBtn.addEventListener("click", togglePlay);
    dom.backBtn.addEventListener("click", () => seekBy(-10));
    dom.forwardBtn.addEventListener("click", () => seekBy(10));
    dom.prevBtn.addEventListener("click", () => stepLesson(-1));
    dom.nextBtn.addEventListener("click", () => stepLesson(1));

    dom.heroPracticeBtn.addEventListener("click", () => openPractice(undefined, dom.heroPracticeBtn));
    dom.closePracticeBtn.addEventListener("click", () => closePractice());
    dom.listenSentenceBtn.addEventListener("click", listenToSentence);
    dom.recordBtn.addEventListener("click", () => {
      if (state.isRecording) {
        stopRecording({ save: true });
      } else {
        startRecording();
      }
    });
    dom.playRecordingBtn.addEventListener("click", playRecording);
    dom.prevSentenceBtn.addEventListener("click", () => goToPracticeLine(state.practiceIndex - 1));
    dom.nextSentenceBtn.addEventListener("click", () => goToPracticeLine(state.practiceIndex + 1));

    dom.transcriptList.addEventListener("click", (event) => {
      const actionTarget = event.target.closest("[data-action]");
      if (!actionTarget) {
        return;
      }
      const row = actionTarget.closest(".sentence");
      if (!row) {
        return;
      }
      const index = Number(row.dataset.index);
      const action = actionTarget.dataset.action;
      if (action === "seek") {
        playFromLine(index);
      } else if (action === "loop") {
        setLoopSentence(true, index);
        playFromLine(index);
      } else if (action === "practice") {
        openPractice(index, actionTarget);
      }
    });

    dom.transcriptList.addEventListener("keydown", (event) => {
      if (!event.target.classList.contains("sentence-main")) {
        return;
      }
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        const row = event.target.closest(".sentence");
        if (row) {
          playFromLine(Number(row.dataset.index));
        }
      }
    });

    dom.backToCategoriesBtn.addEventListener("click", returnToCategories);
    dom.categoryGrid.addEventListener("click", (event) => {
      const card = event.target.closest(".category-card");
      if (!card || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      event.preventDefault();
      openCategory(card.dataset.category);
    });
    window.addEventListener("popstate", () => restoreLibraryRoute());

    dom.episodeList.addEventListener("click", (event) => {
      const episode = event.target.closest(".episode");
      if (!episode) {
        return;
      }
      selectLesson(Number(episode.dataset.index), {
        autoplay: true,
        enterFocus: true,
        scrollToTop: false
      });
    });

    dom.loadMoreBtn.addEventListener("click", () => {
      loadMoreLessons();
    });

    dom.backToBrowseBtn.addEventListener("click", () => returnToBrowse());
    dom.collapseFocusBtn.addEventListener("click", () => returnToBrowse());
    dom.playerTrackBtn.addEventListener("click", () => {
      if (state.view === "focus") {
        dom.readerCard.scrollIntoView({ behavior: scrollBehavior(), block: "start" });
      } else {
        enterFocus();
      }
    });
    dom.shuffleBtn.addEventListener("click", shuffleFeatured);
    dom.favoriteBtn.addEventListener("click", toggleFavorite);

    dom.playlistSearch.addEventListener("input", () => {
      state.filters.query = dom.playlistSearch.value;
      updateSearchClearButton();
      refreshFilteredPlaylist();
    });
    dom.playlistSearch.addEventListener("keydown", (event) => {
      if (event.key !== "Escape" || !state.filters.query) {
        return;
      }
      event.preventDefault();
      state.filters.query = "";
      dom.playlistSearch.value = "";
      updateSearchClearButton();
      renderPlaylist();
    });
    dom.playlistSearchClear.addEventListener("click", () => {
      state.filters.query = "";
      dom.playlistSearch.value = "";
      updateSearchClearButton();
      renderPlaylist();
      dom.playlistSearch.focus({ preventScroll: true });
    });

    dom.translationToggle.addEventListener("click", toggleTranslation);
    dom.followToggle.addEventListener("click", toggleFollow);

    dom.seekSlider.addEventListener("input", handleSeekInput);
    dom.seekSlider.addEventListener("change", finishSeeking);
    dom.seekSlider.addEventListener("pointerup", finishSeeking);
    dom.seekSlider.addEventListener("blur", finishSeeking);
    dom.scrubber.addEventListener("pointermove", showSeekPreview);
    dom.scrubber.addEventListener("pointerleave", hideSeekPreview);

    dom.volumeSlider.addEventListener("input", (event) => {
      setVolume(Number(event.target.value));
    });
    dom.muteBtn.addEventListener("click", toggleMute);

    dom.speedBtn.addEventListener("click", () => {
      if (dom.speedMenu.hidden) {
        openSpeedMenu();
      } else {
        closeSpeedMenu();
      }
    });
    dom.speedMenu.addEventListener("click", (event) => {
      const button = event.target.closest("[data-speed]");
      if (!button) {
        return;
      }
      setSpeed(Number(button.dataset.speed));
      closeSpeedMenu();
      dom.speedBtn.focus({ preventScroll: true });
    });

    dom.loopBtn.addEventListener("click", () => {
      setLoopSentence(!state.loopSentence);
    });

    dom.openHistoryBtn.addEventListener("click", openHistory);
    dom.closeHistoryBtn.addEventListener("click", closeHistory);
    dom.clearHistoryBtn.addEventListener("click", clearHistory);
    dom.historyList.addEventListener("click", (event) => {
      const item = event.target.closest(".history-item");
      if (!item) {
        return;
      }
      playHistoryEntry(item.dataset.id);
    });
    dom.historyDialog.addEventListener("close", () => {
      document.body.classList.remove("is-dialog-open");
    });
    dom.historyDialog.addEventListener("click", (event) => {
      if (event.target !== dom.historyDialog) {
        return;
      }
      const rect = dom.historyDialog.getBoundingClientRect();
      const outsideDialog = event.clientX < rect.left
        || event.clientX > rect.right
        || event.clientY < rect.top
        || event.clientY > rect.bottom;
      if (outsideDialog) {
        closeHistory();
      }
    });

    dom.openShortcutsBtn.addEventListener("click", openShortcuts);
    dom.tipShortcutsBtn.addEventListener("click", openShortcuts);
    dom.closeShortcutsBtn.addEventListener("click", closeShortcuts);
    dom.shortcutsDialog.addEventListener("close", () => {
      document.body.classList.remove("is-dialog-open");
    });
    dom.shortcutsDialog.addEventListener("click", (event) => {
      if (event.target !== dom.shortcutsDialog) {
        return;
      }
      const rect = dom.shortcutsDialog.getBoundingClientRect();
      const outsideDialog = event.clientX < rect.left
        || event.clientX > rect.right
        || event.clientY < rect.top
        || event.clientY > rect.bottom;
      if (outsideDialog) {
        closeShortcuts();
      }
    });

    document.addEventListener("pointerdown", (event) => {
      state.pointerFocusTarget = event.target && event.target.closest
        ? event.target.closest("button, a")
        : null;
      if (!dom.speedMenu.hidden && !event.target.closest(".speed-control")) {
        closeSpeedMenu();
      }
    });

    document.addEventListener("keydown", handleGlobalKeydown);
    window.addEventListener("pagehide", () => {
      saveProgress();
      writeStored(STORAGE_KEYS.settings, {
        speed: state.speed,
        volume: state.volume,
        muted: state.muted,
        translation: state.translation,
        follow: state.follow
      });
    });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") {
        saveProgress();
      }
    });
  }

  function handleGlobalKeydown(event) {
    if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    if (dom.shortcutsDialog.open || dom.historyDialog.open || document.querySelector("#userAuthDialog[open]")) {
      return;
    }
    if (event.key === "Escape") {
      if (!dom.speedMenu.hidden) {
        closeSpeedMenu();
        dom.speedBtn.focus({ preventScroll: true });
      } else if (state.practiceOpen) {
        closePractice();
      }
      return;
    }

    const target = event.target;
    const tagName = target && target.tagName ? target.tagName : "";
    const isTextEntry = tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT"
      || (target && target.isContentEditable);
    if (isTextEntry) {
      return;
    }
    if (event.key === "Tab") {
      state.pointerFocusTarget = null;
      return;
    }

    if (event.code === "Space") {
      // Keep native Space activation when a control was reached by keyboard.
      // Chrome marks a pointer-focused button as :focus-visible as soon as a
      // key is pressed, so also check how the current element was focused.
      if (tagName === "BUTTON" || tagName === "A") {
        let keyboardFocus = true;
        try {
          keyboardFocus = target.matches(":focus-visible");
        } catch (error) {
          keyboardFocus = true;
        }
        if (keyboardFocus && state.pointerFocusTarget !== target) {
          return;
        }
      }
      event.preventDefault();
      togglePlay();
      return;
    }

    if (event.key === "ArrowLeft") {
      event.preventDefault();
      seekBy(-5);
      return;
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      seekBy(5);
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      shiftVolume(0.05);
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      shiftVolume(-0.05);
      return;
    }

    const key = event.key.toLowerCase();
    if (key === "j") {
      event.preventDefault();
      seekBy(-10);
    } else if (key === "l") {
      event.preventDefault();
      seekBy(10);
    } else if (key === "r") {
      event.preventDefault();
      setLoopSentence(!state.loopSentence);
    } else if (key === "t") {
      event.preventDefault();
      toggleTranslation();
    } else if (key === "s") {
      event.preventDefault();
      if (state.practiceOpen) {
        closePractice();
      } else {
        enterFocus();
        openPractice();
      }
    } else if (event.key === "?") {
      event.preventDefault();
      openShortcuts();
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
