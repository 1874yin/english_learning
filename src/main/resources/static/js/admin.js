(() => {
  "use strict";

  const statusApi = window.CadenceTranslationStatus;
  const byId = (id) => document.getElementById(id);
  const dom = {
    loginPanel: byId("loginPanel"),
    loginForm: byId("loginForm"),
    loginBtn: byId("loginBtn"),
    loginMessage: byId("loginMessage"),
    adminPassword: byId("adminPassword"),
    adminPanel: byId("adminPanel"),
    logoutBtn: byId("logoutBtn"),
    libraryCount: byId("libraryCount"),
    translationMeta: byId("translationMeta"),
    translationBadges: byId("translationBadges"),
    translationRefreshBtn: byId("translationRefreshBtn"),
    translationLineCount: byId("translationLineCount"),
    translationLinePercent: byId("translationLinePercent"),
    translationLineBar: byId("translationLineBar"),
    translationLineFill: byId("translationLineFill"),
    translationSummary: byId("translationSummary"),
    translationError: byId("translationError"),
    lessonList: byId("lessonList"),
    lessonForm: byId("lessonForm"),
    formTitle: byId("formTitle"),
    resetFormBtn: byId("resetFormBtn"),
    title: byId("title"),
    titleZh: byId("titleZh"),
    category: byId("category"),
    categorySelect: byId("categorySelect"),
    categoryChips: byId("categoryChips"),
    categoryHint: byId("categoryHint"),
    addCategoryBtn: byId("addCategoryBtn"),
    level: byId("level"),
    summary: byId("summary"),
    summaryZh: byId("summaryZh"),
    audioFile: byId("audioFile"),
    audioRequiredMark: byId("audioRequiredMark"),
    audioHint: byId("audioHint"),
    coverFile: byId("coverFile"),
    coverHint: byId("coverHint"),
    coverPreview: byId("coverPreview"),
    transcriptText: byId("transcriptText"),
    wordsText: byId("wordsText"),
    submitBtn: byId("submitBtn"),
    submitStatus: byId("submitStatus"),
    successPanel: byId("successPanel"),
    successTitle: byId("successTitle"),
    successText: byId("successText"),
    successLink: byId("successLink")
  };

  const defaultAudioHint = "最大 50 MB。上传后会读取实际音频时长。";
  const defaultCover = "/images/cover-default.svg";
  const defaultCoverHint = "JPG、PNG、GIF 或 AVIF，最大 5 MB，建议使用横图。不选择时使用默认封面。";
  const defaultCategoryHint = "从上方选择已有分类，或输入新分类后点“添加”；保存素材时使用的分类也会自动出现在这里。";
  const state = {
    lessons: [],
    categories: [],
    editingId: null,
    coverUrl: defaultCover,
    busy: false,
    addingCategory: false,
    localAccess: false,
    translationLoading: false,
    translationAutoRefresh: false,
    translationLoaded: false
  };
  let coverObjectUrl = null;

  function setCoverPreview(source) {
    if (coverObjectUrl) {
      URL.revokeObjectURL(coverObjectUrl);
      coverObjectUrl = null;
    }
    dom.coverPreview.src = source;
  }

  function setSelectedCoverPreview(file) {
    if (coverObjectUrl) {
      URL.revokeObjectURL(coverObjectUrl);
    }
    coverObjectUrl = URL.createObjectURL(file);
    dom.coverPreview.src = coverObjectUrl;
  }

  function formatTime(seconds) {
    const value = Number(seconds);
    if (!Number.isFinite(value) || value < 0) {
      return "0:00";
    }
    const whole = Math.floor(value + 0.001);
    const minutes = Math.floor(whole / 60);
    const rest = whole % 60;
    return minutes + ":" + String(rest).padStart(2, "0");
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

  function setLoginMessage(message) {
    dom.loginMessage.textContent = message || "";
  }

  function setStatus(message, kind) {
    dom.submitStatus.textContent = message || "";
    dom.submitStatus.classList.toggle("is-error", kind === "error");
    dom.submitStatus.classList.toggle("is-success", kind === "success");
  }

  function showLogin(expired) {
    state.translationAutoRefresh = false;
    dom.adminPanel.hidden = true;
    dom.loginPanel.hidden = false;
    dom.logoutBtn.hidden = true;
    setLoginMessage(expired ? "登录已过期，请重新登录。" : "");
    window.setTimeout(() => dom.adminPassword.focus(), 50);
  }

  function showPanel() {
    dom.loginPanel.hidden = true;
    dom.adminPanel.hidden = false;
    dom.logoutBtn.hidden = state.localAccess;
    setLoginMessage("");
  }

  async function readError(response, fallback) {
    try {
      const data = await response.json();
      if (data && typeof data.message === "string" && data.message) {
        return data.message;
      }
    } catch (error) {
      /* Fall through to the generic message. */
    }
    return fallback;
  }

  async function refreshSession() {
    try {
      const response = await fetch("/api/admin/lessons", {
        headers: { Accept: "application/json" }
      });
      if (response.status === 401) {
        showLogin(false);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "无法读取素材列表。"));
      }
      state.localAccess = response.headers.get("X-Admin-Local-Access") === "true";
      const lessons = await response.json();
      showPanel();
      renderList(Array.isArray(lessons) ? lessons : []);
      await refreshCategories();
      refreshTranslationStatus();
      if (!state.editingId) {
        resetForm({ keepStatus: true });
      }
    } catch (error) {
      showLogin(false);
      setLoginMessage(error.message || "无法连接素材服务，请刷新重试。");
    }
  }

  async function refreshCategories() {
    try {
      const response = await fetch("/api/categories", {
        headers: { Accept: "application/json" }
      });
      if (!response.ok) {
        return;
      }
      const categories = await response.json();
      applyCategories(Array.isArray(categories) ? categories : []);
    } catch (error) {
      /* Category suggestions are optional; keep the current list. */
    }
  }

  function setTranslationLoading(loading) {
    dom.translationRefreshBtn.disabled = loading;
    dom.translationRefreshBtn.classList.toggle("is-loading", loading);
    dom.translationRefreshBtn.setAttribute("aria-busy", loading ? "true" : "false");
  }

  function renderTranslationBadges(badges) {
    dom.translationBadges.replaceChildren();
    badges.forEach((badge) => {
      dom.translationBadges.appendChild(
        createElement("span", "translation-badge is-" + badge.tone, badge.label)
      );
    });
  }

  function renderTranslationStatus(view) {
    dom.translationMeta.textContent = statusApi.buildMeta(view);
    renderTranslationBadges(statusApi.buildBadges(view));
    dom.translationLineCount.textContent = statusApi.formatCount(view.translatedLines)
      + " / " + statusApi.formatCount(view.totalLines) + " 句";
    dom.translationLinePercent.textContent = view.mode === "empty"
      ? "—"
      : statusApi.formatPercent(view.linePercent);

    const percent = Math.max(0, Math.min(100, Number(view.linePercent) || 0));
    dom.translationLineFill.style.width = percent + "%";
    dom.translationLineBar.setAttribute("aria-valuenow", String(percent));
    dom.translationLineBar.setAttribute("aria-valuetext", statusApi.buildAriaText(view));

    dom.translationSummary.textContent = statusApi.buildSummary(view);
    dom.translationError.hidden = true;
    dom.translationError.textContent = "";
    state.translationAutoRefresh = view.autoRefresh;
  }

  function renderTranslationError(message) {
    dom.translationError.textContent = (state.translationLoaded ? "更新失败：" : "暂时无法读取：")
      + (message || "翻译状态暂时无法读取。");
    dom.translationError.hidden = false;
    if (state.translationLoaded) {
      return;
    }
    dom.translationMeta.textContent = "翻译状态暂时不可用";
    dom.translationLineCount.textContent = "—";
    dom.translationLinePercent.textContent = "—";
    dom.translationSummary.textContent = "";
    dom.translationLineFill.style.width = "0%";
    dom.translationLineBar.setAttribute("aria-valuenow", "0");
    dom.translationLineBar.setAttribute("aria-valuetext", "翻译状态暂时无法读取");
    dom.translationBadges.replaceChildren();
  }

  async function refreshTranslationStatus(options) {
    const opts = Object.assign({ quiet: false }, options || {});
    if (state.translationLoading) {
      return;
    }
    state.translationLoading = true;
    setTranslationLoading(true);
    if (!opts.quiet && !state.translationLoaded) {
      dom.translationMeta.textContent = "正在读取翻译状态…";
    }

    try {
      const response = await fetch("/api/admin/translation/status", {
        headers: { Accept: "application/json" }
      });
      if (response.status === 401) {
        showLogin(true);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "无法读取翻译状态。"));
      }
      const snapshot = await response.json();
      const view = statusApi.buildViewModel(snapshot);
      state.translationLoaded = true;
      renderTranslationStatus(view);
    } catch (error) {
      renderTranslationError(error.message);
    } finally {
      state.translationLoading = false;
      setTranslationLoading(false);
    }
  }

  function maybeAutoRefreshTranslationStatus() {
    if (document.visibilityState !== "visible" || dom.adminPanel.hidden) {
      return;
    }
    if (!state.translationAutoRefresh || state.translationLoading) {
      return;
    }
    refreshTranslationStatus({ quiet: true });
  }

  function applyCategories(categories) {
    state.categories = categories.filter((name) => typeof name === "string" && name.trim());
    renderCategories();
  }

  function syncCategorySelect(value) {
    const normalized = typeof value === "string" ? value.trim() : "";
    const match = normalized
      ? state.categories.find((name) => name.toLowerCase() === normalized.toLowerCase())
      : null;
    dom.categorySelect.value = match || "";
  }

  function renderCategories() {
    dom.categorySelect.replaceChildren();
    const placeholder = createElement("option", "", "选择已有分类…");
    placeholder.value = "";
    dom.categorySelect.appendChild(placeholder);
    state.categories.forEach((name) => {
      const option = createElement("option", "", name);
      option.value = name;
      dom.categorySelect.appendChild(option);
    });
    syncCategorySelect(dom.category.value);

    dom.categoryChips.replaceChildren();
    state.categories.forEach((name) => {
      const chip = createElement("span", "category-chip");
      chip.appendChild(createElement("span", "category-chip-name", name));
      const remove = createElement("button", "category-chip-remove", "×");
      remove.type = "button";
      remove.setAttribute("aria-label", "删除分类：" + name);
      remove.title = "从可选分类中删除";
      remove.addEventListener("click", () => removeCategory(name, remove));
      chip.appendChild(remove);
      dom.categoryChips.appendChild(chip);
    });
  }

  function setCategoryHint(message, kind) {
    dom.categoryHint.textContent = message || defaultCategoryHint;
    dom.categoryHint.classList.toggle("is-error", kind === "error");
    dom.categoryHint.classList.toggle("is-success", kind === "success");
  }

  async function addCategory() {
    if (state.addingCategory) {
      return;
    }
    const name = dom.category.value.trim().replace(/\s+/g, " ");
    if (!name) {
      setCategoryHint("先输入分类名称，再点击“添加”。", "error");
      dom.category.focus();
      return;
    }
    const existing = state.categories.find(
      (category) => category.toLowerCase() === name.toLowerCase()
    );
    if (existing) {
      dom.category.value = existing;
      setCategoryHint("这个分类已经在列表中了。", "success");
      return;
    }

    state.addingCategory = true;
    dom.addCategoryBtn.disabled = true;
    try {
      const response = await fetch("/api/admin/categories", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json"
        },
        body: JSON.stringify({ name: name })
      });
      if (response.status === 401) {
        showLogin(true);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "无法添加分类。"));
      }
      const categories = await response.json();
      dom.category.value = name;
      applyCategories(Array.isArray(categories) ? categories : []);
      setCategoryHint("已添加分类“" + name + "”。", "success");
    } catch (error) {
      setCategoryHint(error.message || "无法添加分类，请重试。", "error");
    } finally {
      state.addingCategory = false;
      dom.addCategoryBtn.disabled = false;
    }
  }

  async function removeCategory(name, button) {
    button.disabled = true;
    try {
      const response = await fetch("/api/admin/categories?name=" + encodeURIComponent(name), {
        method: "DELETE",
        headers: { Accept: "application/json" }
      });
      if (response.status === 401) {
        showLogin(true);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "无法删除这个分类。"));
      }
      state.categories = state.categories.filter((category) => category !== name);
      renderCategories();
      setCategoryHint("已删除分类“" + name + "”。", "success");
    } catch (error) {
      button.disabled = false;
      setCategoryHint(error.message || "无法删除这个分类。", "error");
    }
  }

  async function refreshList() {
    const response = await fetch("/api/admin/lessons", {
      headers: { Accept: "application/json" }
    });
    if (response.status === 401) {
      showLogin(true);
      throw new Error("unauthorized");
    }
    if (!response.ok) {
      throw new Error(await readError(response, "无法刷新素材列表。"));
    }
    const lessons = await response.json();
    renderList(Array.isArray(lessons) ? lessons : []);
  }

  function renderList(lessons) {
    state.lessons = lessons;
    dom.libraryCount.textContent = lessons.length + " 节素材";
    dom.lessonList.replaceChildren();

    if (!lessons.length) {
      dom.lessonList.appendChild(createElement(
        "p",
        "admin-empty",
        "还没有素材。在右侧填写标题、上传 MP3 和台词，添加后会显示在这里。"
      ));
      return;
    }

    lessons.forEach((lesson) => {
      const item = createElement("article", "admin-lesson");
      if (lesson.id === state.editingId) {
        item.classList.add("is-editing");
      }

      const cover = createElement("img", "admin-lesson-cover");
      cover.src = lesson.cover || defaultCover;
      cover.alt = "";
      cover.loading = "lazy";
      cover.width = 56;
      cover.height = 48;

      const copy = createElement("div", "admin-lesson-copy");
      copy.appendChild(createElement("h3", "", lesson.title || "未命名素材"));
      if (lesson.titleZh) {
        copy.appendChild(createElement("p", "lesson-zh", lesson.titleZh));
      }

      const meta = createElement("div", "admin-lesson-meta");
      meta.appendChild(createElement("span", "", "第 " + (lesson.number || "--") + " 期"));
      if (lesson.category) {
        meta.appendChild(createElement("span", "", lesson.category));
      }
      if (lesson.level) {
        meta.appendChild(createElement("span", "", lesson.level));
      }
      meta.appendChild(createElement("span", "", formatTime(lesson.duration)));
      meta.appendChild(createElement("span", "", (lesson.sentenceCount || 0) + " 句"));
      copy.appendChild(meta);

      const edit = createElement("button", "admin-edit", "编辑");
      edit.type = "button";
      edit.setAttribute("aria-label", "编辑素材：" + (lesson.title || "未命名素材"));
      edit.addEventListener("click", () => loadLesson(lesson.id));

      item.appendChild(cover);
      item.appendChild(copy);
      item.appendChild(edit);
      dom.lessonList.appendChild(item);
    });
  }

  async function loadLesson(id) {
    setStatus("正在载入素材…");
    try {
      const response = await fetch("/api/admin/lessons/" + encodeURIComponent(id), {
        headers: { Accept: "application/json" }
      });
      if (response.status === 401) {
        showLogin(true);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "无法读取这节素材。"));
      }
      const lesson = await response.json();
      state.editingId = lesson.id;
      dom.formTitle.textContent = "编辑素材";
      dom.submitBtn.textContent = "保存修改";
      dom.resetFormBtn.hidden = false;
      dom.successPanel.hidden = true;
      dom.title.value = lesson.title || "";
      dom.titleZh.value = lesson.titleZh || "";
      dom.category.value = lesson.category || "Listening";
      syncCategorySelect(dom.category.value);
      dom.level.value = lesson.level || "Intermediate";
      dom.summary.value = lesson.summary || "";
      dom.summaryZh.value = lesson.summaryZh || "";
      dom.transcriptText.value = (lesson.transcript || []).map((line) => line.text).join("\n");
      dom.wordsText.value = lesson.wordsText || "";
      dom.audioFile.value = "";
      dom.audioFile.required = false;
      dom.audioRequiredMark.hidden = true;
      dom.audioHint.textContent = "当前音频 " + formatTime(lesson.duration)
        + "。不选择新文件则保留原音频。";
      state.coverUrl = lesson.cover || defaultCover;
      setCoverPreview(state.coverUrl);
      dom.coverFile.value = "";
      dom.coverHint.textContent = "不选择新图片则保留当前封面。";
      setStatus("");
      renderList(state.lessons);
      dom.lessonForm.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
      setStatus(error.message || "载入失败，请重试。", "error");
    }
  }

  function resetForm(options) {
    const opts = Object.assign({ keepStatus: false }, options || {});
    state.editingId = null;
    dom.lessonForm.reset();
    dom.category.value = "Listening";
    syncCategorySelect(dom.category.value);
    dom.level.value = "Intermediate";
    dom.formTitle.textContent = "添加素材";
    dom.submitBtn.textContent = "添加素材";
    dom.resetFormBtn.hidden = true;
    dom.audioFile.required = true;
    dom.audioRequiredMark.hidden = false;
    dom.audioHint.textContent = defaultAudioHint;
    state.coverUrl = defaultCover;
    dom.coverFile.value = "";
    dom.coverHint.textContent = defaultCoverHint;
    setCoverPreview(defaultCover);
    dom.wordsText.value = "";
    dom.successPanel.hidden = true;
    dom.title.removeAttribute("aria-invalid");
    dom.transcriptText.removeAttribute("aria-invalid");
    if (!opts.keepStatus) {
      setStatus("");
    }
    renderList(state.lessons);
    if (!opts.keepStatus) {
      dom.title.focus();
    }
  }

  function showSuccess(lesson, wasEditing) {
    const time = formatTime(lesson.duration);
    const count = Array.isArray(lesson.transcript) ? lesson.transcript.length : 0;
    dom.successTitle.textContent = wasEditing ? "素材已更新" : "素材已添加";
    dom.successText.textContent = "《" + lesson.title + "》 · " + time
      + " · 后端自动生成 " + count + " 句时间轴。";
    dom.successLink.href = "/?lesson=" + encodeURIComponent(lesson.id);
    dom.successPanel.hidden = false;
  }

  async function handleLogin(event) {
    event.preventDefault();
    if (dom.loginBtn.disabled) {
      return;
    }
    const password = dom.adminPassword.value;
    if (!password) {
      setLoginMessage("请输入管理密码。");
      dom.adminPassword.focus();
      return;
    }

    dom.loginBtn.disabled = true;
    dom.loginBtn.textContent = "登录中…";
    setLoginMessage("");
    try {
      const response = await fetch("/api/admin/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json"
        },
        body: JSON.stringify({ password: password })
      });
      if (!response.ok) {
        throw new Error(await readError(response, "登录失败，请检查密码。"));
      }
      dom.adminPassword.value = "";
      await refreshSession();
    } catch (error) {
      setLoginMessage(error.message || "登录失败，请重试。");
    } finally {
      dom.loginBtn.disabled = false;
      dom.loginBtn.textContent = "登录";
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (state.busy) {
      return;
    }

    const title = dom.title.value.trim();
    const transcript = dom.transcriptText.value.trim();
    if (!title) {
      dom.title.setAttribute("aria-invalid", "true");
      setStatus("请填写英文标题。", "error");
      dom.title.focus();
      return;
    }
    dom.title.removeAttribute("aria-invalid");

    if (!transcript) {
      dom.transcriptText.setAttribute("aria-invalid", "true");
      setStatus("请填写台词文本。", "error");
      dom.transcriptText.focus();
      return;
    }
    dom.transcriptText.removeAttribute("aria-invalid");

    if (!state.editingId && !dom.audioFile.files.length) {
      setStatus("添加素材时必须选择一个 MP3 文件。", "error");
      dom.audioFile.focus();
      return;
    }

    // Build FormData before disabling controls: disabled fields are omitted.
    const editingId = state.editingId;
    const formData = new FormData(dom.lessonForm);

    state.busy = true;
    dom.submitBtn.disabled = true;
    dom.resetFormBtn.disabled = true;
    dom.audioFile.disabled = true;
    dom.coverFile.disabled = true;
    setStatus("正在上传并分析音频，请稍候…");

    try {
      const response = await fetch(
        editingId ? "/api/admin/lessons/" + encodeURIComponent(editingId) : "/api/admin/lessons",
        {
          method: editingId ? "PUT" : "POST",
          headers: { Accept: "application/json" },
          body: formData
        }
      );
      if (response.status === 401) {
        showLogin(true);
        return;
      }
      if (!response.ok) {
        throw new Error(await readError(response, "保存失败，请稍后重试。"));
      }

      const lesson = await response.json();
      let listRefreshFailed = false;
      try {
        await refreshList();
      } catch (refreshError) {
        if (refreshError.message === "unauthorized") {
          showLogin(true);
          return;
        }
        listRefreshFailed = true;
      }
      await refreshCategories();
      refreshTranslationStatus({ quiet: true });
      resetForm({ keepStatus: true });
      setStatus(
        listRefreshFailed
          ? "已保存，但素材列表刷新失败，请手动刷新页面。"
          : (editingId ? "修改已保存。" : "素材已添加。"),
        "success"
      );
      showSuccess(lesson, Boolean(editingId));
    } catch (error) {
      setStatus(error.message || "保存失败，请稍后重试。", "error");
    } finally {
      state.busy = false;
      dom.submitBtn.disabled = false;
      dom.resetFormBtn.disabled = false;
      dom.audioFile.disabled = false;
      dom.coverFile.disabled = false;
    }
  }

  async function handleLogout() {
    dom.logoutBtn.disabled = true;
    try {
      await fetch("/api/admin/logout", { method: "DELETE" });
    } catch (error) {
      /* The session may already be gone; show the login form either way. */
    } finally {
      dom.logoutBtn.disabled = false;
      state.editingId = null;
      resetForm();
      showLogin(false);
    }
  }

  function handleFileChange() {
    const file = dom.audioFile.files[0];
    if (!file) {
      dom.audioHint.textContent = state.editingId
        ? "不选择新文件则保留原音频。"
        : defaultAudioHint;
      return;
    }
    const size = file.size / (1024 * 1024);
    dom.audioHint.textContent = file.name + " · " + size.toFixed(1) + " MB";
  }

  function handleCoverChange() {
    const file = dom.coverFile.files[0];
    if (!file) {
      setCoverPreview(state.coverUrl);
      dom.coverHint.textContent = state.editingId
        ? "不选择新图片则保留当前封面。"
        : defaultCoverHint;
      return;
    }
    setSelectedCoverPreview(file);
    const size = file.size / (1024 * 1024);
    dom.coverHint.textContent = size > 5
      ? file.name + " · 超过 5 MB，请更换图片。"
      : file.name + " · " + size.toFixed(1) + " MB";
  }

  function init() {
    dom.loginForm.addEventListener("submit", handleLogin);
    dom.lessonForm.addEventListener("submit", handleSubmit);
    dom.addCategoryBtn.addEventListener("click", addCategory);
    dom.categorySelect.addEventListener("change", () => {
      const selected = dom.categorySelect.value;
      if (!selected) {
        return;
      }
      dom.category.value = selected;
      setCategoryHint("已选择分类“" + selected + "”。", "success");
    });
    dom.category.addEventListener("input", () => {
      syncCategorySelect(dom.category.value);
      setCategoryHint("");
    });
    dom.category.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        addCategory();
      }
    });
    dom.resetFormBtn.addEventListener("click", () => resetForm());
    dom.logoutBtn.addEventListener("click", handleLogout);
    dom.audioFile.addEventListener("change", handleFileChange);
    dom.coverFile.addEventListener("change", handleCoverChange);
    dom.translationRefreshBtn.addEventListener("click", () => refreshTranslationStatus());
    window.setInterval(maybeAutoRefreshTranslationStatus, 15000);
    document.addEventListener("visibilitychange", maybeAutoRefreshTranslationStatus);
    window.addEventListener("beforeunload", () => {
      if (coverObjectUrl) {
        URL.revokeObjectURL(coverObjectUrl);
      }
    });
    resetForm({ keepStatus: true });
    refreshCategories();
    refreshSession();
  }

  init();
})();
