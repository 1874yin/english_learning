const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildViewModel,
  buildBadges,
  buildMeta,
  buildSummary,
  buildAriaText,
  progressPercent,
  formatCount,
  formatPercent,
  shouldAutoRefresh
} = require("../../main/resources/static/js/translation-status.js");

test("progressPercent is zero when there are no lines", () => {
  assert.equal(progressPercent(0, 0), 0);
  assert.equal(progressPercent(12, 0), 0);
});

test("progressPercent rounds to one decimal and stays inside the bar", () => {
  assert.equal(progressPercent(126, 10368), 1.2);
  assert.equal(progressPercent(1, 3), 33.3);
  assert.equal(progressPercent(200, 100), 100);
  assert.equal(progressPercent(-5, 100), 0);
});

test("progressPercent never rounds an unfinished library up to 100", () => {
  assert.equal(progressPercent(10367, 10368), 99.9);
});

test("formatCount groups thousands", () => {
  assert.equal(formatCount(0), "0");
  assert.equal(formatCount(999), "999");
  assert.equal(formatCount(10368), "10,368");
  assert.equal(formatCount(1000000), "1,000,000");
});

test("formatPercent drops trailing zeros but keeps tiny progress visible", () => {
  assert.equal(formatPercent(0), "0%");
  assert.equal(formatPercent(100), "100%");
  assert.equal(formatPercent(12), "12%");
  assert.equal(formatPercent(1.2), "1.2%");
  assert.equal(formatPercent(0.04), "<0.1%");
  assert.equal(formatPercent(-2), "0%");
  assert.equal(formatPercent(140), "100%");
});

test("buildViewModel maps the translation status snapshot", () => {
  const view = buildViewModel({
    enabled: true,
    provider: "deepseek",
    model: "deepseek-chat",
    stateCurrent: true,
    storedProvider: "deepseek",
    storedVersion: 1,
    totalLessons: 265,
    totalLines: 10368,
    translatedLines: 126,
    missingLines: 10242,
    translatedLessons: 3,
    missingLessons: 262
  });

  assert.equal(view.enabled, true);
  assert.equal(view.provider, "deepseek");
  assert.equal(view.model, "deepseek-chat");
  assert.equal(view.stateCurrent, true);
  assert.equal(view.providerChanged, false);
  assert.equal(view.totalLessons, 265);
  assert.equal(view.totalLines, 10368);
  assert.equal(view.translatedLines, 126);
  assert.equal(view.missingLines, 10242);
  assert.equal(view.translatedLessons, 3);
  assert.equal(view.missingLessons, 262);
  assert.equal(view.linePercent, 1.2);
  assert.equal(view.lessonPercent, 1.1);
  assert.equal(view.mode, "partial");
  assert.equal(view.autoRefresh, true);
});

test("buildViewModel flags a provider switch recorded in the state file", () => {
  const view = buildViewModel({
    enabled: true,
    provider: "deepseek",
    storedProvider: "openai",
    storedVersion: 1,
    totalLessons: 1,
    totalLines: 10,
    translatedLines: 10,
    missingLines: 0,
    translatedLessons: 1,
    missingLessons: 0
  });

  assert.equal(view.providerChanged, true);
  assert.equal(view.stateCurrent, false);
  assert.equal(view.mode, "complete");
  assert.equal(view.autoRefresh, false);
});

test("buildViewModel treats a missing snapshot as an empty, disabled library", () => {
  const view = buildViewModel(null);

  assert.equal(view.enabled, false);
  assert.equal(view.provider, "");
  assert.equal(view.model, "");
  assert.equal(view.stateCurrent, false);
  assert.equal(view.totalLessons, 0);
  assert.equal(view.totalLines, 0);
  assert.equal(view.translatedLines, 0);
  assert.equal(view.missingLines, 0);
  assert.equal(view.linePercent, 0);
  assert.equal(view.mode, "empty");
  assert.equal(view.autoRefresh, false);
});

test("buildViewModel derives missing counts when the API omits them", () => {
  const view = buildViewModel({
    totalLessons: 4,
    totalLines: 20,
    translatedLines: 8,
    translatedLessons: 1
  });

  assert.equal(view.missingLines, 12);
  assert.equal(view.missingLessons, 3);
});

test("shouldAutoRefresh only runs for visible, enabled, unfinished translation", () => {
  const view = buildViewModel({
    enabled: true,
    totalLessons: 2,
    totalLines: 20,
    translatedLines: 8,
    translatedLessons: 1,
    missingLines: 12,
    missingLessons: 1
  });

  assert.equal(shouldAutoRefresh(view, true), true);
  assert.equal(shouldAutoRefresh(view, false), false);
  assert.equal(shouldAutoRefresh(Object.assign({}, view, { enabled: false }), true), false);
  assert.equal(shouldAutoRefresh(Object.assign({}, view, { missingLines: 0 }), true), false);
});

test("buildBadges reports service and cleanup state with labels and tones", () => {
  const view = buildViewModel({
    enabled: true,
    provider: "deepseek",
    stateCurrent: true,
    storedProvider: "deepseek",
    totalLessons: 1,
    totalLines: 10,
    translatedLines: 10,
    missingLines: 0,
    missingLessons: 0
  });

  assert.deepEqual(buildBadges(view), [
    { label: "服务已启用", tone: "good" },
    { label: "旧译文已清理", tone: "good" }
  ]);
});

test("buildBadges warns when translation is off or old translations remain", () => {
  const view = buildViewModel({
    enabled: false,
    stateCurrent: false,
    totalLessons: 1,
    totalLines: 10
  });

  assert.deepEqual(buildBadges(view), [
    { label: "服务未启用", tone: "warn" },
    { label: "旧译文待清理", tone: "warn" }
  ]);
});

test("buildBadges calls out a provider switch recorded on disk", () => {
  const view = buildViewModel({
    enabled: true,
    provider: "deepseek",
    stateCurrent: false,
    storedProvider: "openai",
    storedVersion: 1
  });

  assert.deepEqual(buildBadges(view), [
    { label: "服务已启用", tone: "good" },
    { label: "旧译文待清理", tone: "warn" },
    { label: "提供方已变更", tone: "warn" }
  ]);
});

test("buildMeta names the provider, model and stored state", () => {
  const view = buildViewModel({
    provider: "deepseek",
    model: "deepseek-chat",
    storedProvider: "deepseek",
    storedVersion: 1
  });

  assert.equal(
    buildMeta(view),
    "提供方 deepseek · 模型 deepseek-chat · 状态记录 deepseek v1"
  );
});

test("buildMeta handles a snapshot without provider details", () => {
  assert.equal(buildMeta(buildViewModel(null)), "提供方未配置 · 模型未配置");
});

test("buildSummary describes an empty library", () => {
  assert.equal(
    buildSummary(buildViewModel(null)),
    "素材库为空。添加素材后，这里会显示自动翻译进度。"
  );
});

test("buildSummary describes a completed library", () => {
  const view = buildViewModel({
    enabled: true,
    totalLessons: 265,
    totalLines: 10368,
    translatedLines: 10368,
    missingLines: 0,
    translatedLessons: 265,
    missingLessons: 0
  });

  assert.equal(buildSummary(view), "全部 265 节素材已完成翻译。");
});

test("buildSummary shows lesson and line counts while translation is running", () => {
  const view = buildViewModel({
    enabled: true,
    totalLessons: 265,
    totalLines: 10368,
    translatedLines: 126,
    missingLines: 10242,
    translatedLessons: 3,
    missingLessons: 262
  });

  assert.equal(
    buildSummary(view),
    "完成 3 / 265 节 · 待翻译 10,242 句 · 262 节未完成。"
  );
});

test("buildSummary explains why an unfinished library will not progress", () => {
  const view = buildViewModel({
    enabled: false,
    totalLessons: 2,
    totalLines: 20,
    translatedLines: 8,
    missingLines: 12,
    translatedLessons: 1,
    missingLessons: 1
  });

  assert.equal(
    buildSummary(view),
    "完成 1 / 2 节 · 待翻译 12 句 · 1 节未完成。自动翻译未启用，已有台词不会继续补翻。"
  );
});

test("buildAriaText states progress for assistive technology", () => {
  const view = buildViewModel({
    current: true,
    totalLessons: 265,
    totalLines: 10368,
    translatedLines: 126,
    missingLines: 10242,
    translatedLessons: 3,
    missingLessons: 262
  });

  assert.equal(
    buildAriaText(view),
    "已翻译 126 / 10,368 句，占 1.2%，待翻译 10,242 句"
  );
  assert.equal(buildAriaText(buildViewModel(null)), "暂无台词数据");
});
