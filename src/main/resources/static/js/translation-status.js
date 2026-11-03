(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.CadenceTranslationStatus = api;
  }
})(typeof window === "object" ? window : null, function () {
  "use strict";

  function toCount(value) {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
  }

  function hasCount(value) {
    return typeof value === "number" && Number.isFinite(value);
  }

  function toText(value) {
    return typeof value === "string" ? value.trim() : "";
  }

  function progressPercent(translated, total) {
    const totalCount = toCount(total);
    if (!totalCount) {
      return 0;
    }
    const translatedCount = Math.min(toCount(translated), totalCount);
    const ratio = (translatedCount / totalCount) * 100;
    if (ratio > 0 && ratio < 100) {
      return Math.min(Math.round(ratio * 10) / 10, 99.9);
    }
    return ratio;
  }

  function formatCount(value) {
    return String(toCount(value)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  }

  function formatPercent(value) {
    const number = Number(value);
    if (!Number.isFinite(number) || number <= 0) {
      return "0%";
    }
    if (number >= 100) {
      return "100%";
    }
    if (number < 0.1) {
      return "<0.1%";
    }
    const truncated = Math.floor(number * 10) / 10;
    return (Number.isInteger(truncated) ? String(truncated) : truncated.toFixed(1)) + "%";
  }

  function buildViewModel(snapshot) {
    const raw = snapshot && typeof snapshot === "object" ? snapshot : {};
    const totalLessons = toCount(raw.totalLessons);
    const totalLines = toCount(raw.totalLines);
    const translatedLines = toCount(raw.translatedLines);
    const translatedLessons = toCount(raw.translatedLessons);
    const missingLines = hasCount(raw.missingLines)
      ? toCount(raw.missingLines)
      : Math.max(0, totalLines - translatedLines);
    const missingLessons = hasCount(raw.missingLessons)
      ? toCount(raw.missingLessons)
      : Math.max(0, totalLessons - translatedLessons);
    const provider = toText(raw.provider);
    const storedProvider = toText(raw.storedProvider);
    const enabled = raw.enabled === true;
    const stateCurrent = raw.stateCurrent === true;
    const mode = totalLines === 0 ? "empty" : missingLines === 0 ? "complete" : "partial";

    return {
      enabled: enabled,
      provider: provider,
      model: toText(raw.model),
      stateCurrent: stateCurrent,
      storedProvider: storedProvider,
      storedVersion: hasCount(raw.storedVersion) ? Math.floor(raw.storedVersion) : null,
      providerChanged: Boolean(
        provider && storedProvider && provider.toLowerCase() !== storedProvider.toLowerCase()
      ),
      totalLessons: totalLessons,
      totalLines: totalLines,
      translatedLines: translatedLines,
      missingLines: missingLines,
      translatedLessons: translatedLessons,
      missingLessons: missingLessons,
      linePercent: progressPercent(translatedLines, totalLines),
      lessonPercent: progressPercent(translatedLessons, totalLessons),
      mode: mode,
      autoRefresh: enabled && missingLines > 0
    };
  }

  function shouldAutoRefresh(view, visible) {
    return Boolean(
      visible
      && view
      && view.enabled
      && view.missingLines > 0
    );
  }

  function buildBadges(view) {
    const badges = [
      view.enabled
        ? { label: "服务已启用", tone: "good" }
        : { label: "服务未启用", tone: "warn" },
      view.stateCurrent
        ? { label: "旧译文已清理", tone: "good" }
        : { label: "旧译文待清理", tone: "warn" }
    ];
    if (view.providerChanged) {
      badges.push({ label: "提供方已变更", tone: "warn" });
    }
    return badges;
  }

  function buildMeta(view) {
    const parts = [
      view.provider ? "提供方 " + view.provider : "提供方未配置",
      view.model ? "模型 " + view.model : "模型未配置"
    ];
    if (view.storedProvider) {
      parts.push("状态记录 " + view.storedProvider
        + (view.storedVersion !== null ? " v" + view.storedVersion : ""));
    }
    return parts.join(" · ");
  }

  function buildSummary(view) {
    if (view.mode === "empty") {
      return "素材库为空。添加素材后，这里会显示自动翻译进度。";
    }
    if (view.mode === "complete") {
      return "全部 " + formatCount(view.totalLessons) + " 节素材已完成翻译。";
    }
    const summary = "完成 " + formatCount(view.translatedLessons) + " / "
      + formatCount(view.totalLessons) + " 节 · 待翻译 "
      + formatCount(view.missingLines) + " 句 · "
      + formatCount(view.missingLessons) + " 节未完成。";
    return view.enabled
      ? summary
      : summary + "自动翻译未启用，已有台词不会继续补翻。";
  }

  function buildAriaText(view) {
    if (view.mode === "empty") {
      return "暂无台词数据";
    }
    return "已翻译 " + formatCount(view.translatedLines) + " / "
      + formatCount(view.totalLines) + " 句，占 "
      + formatPercent(view.linePercent) + "，待翻译 "
      + formatCount(view.missingLines) + " 句";
  }

  return {
    buildViewModel: buildViewModel,
    buildBadges: buildBadges,
    buildMeta: buildMeta,
    buildSummary: buildSummary,
    buildAriaText: buildAriaText,
    progressPercent: progressPercent,
    formatCount: formatCount,
    formatPercent: formatPercent,
    shouldAutoRefresh: shouldAutoRefresh
  };
});
