(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.CadenceLibrary = api;
  }
})(typeof window === "object" ? window : null, function () {
  "use strict";

  function pickRandomIndex(count, excludeIndex, randomFn) {
    const total = Number.isInteger(count) ? count : 0;
    if (total <= 0) {
      return -1;
    }
    if (total === 1) {
      return 0;
    }
    const random = typeof randomFn === "function" ? randomFn : Math.random;
    const excluded = Number.isInteger(excludeIndex)
      && excludeIndex >= 0
      && excludeIndex < total
      ? excludeIndex
      : -1;
    let index = Math.floor(random() * total);
    if (index === excluded) {
      index = (index + 1 + Math.floor(random() * (total - 1))) % total;
    }
    return index;
  }

  function resolveInitialIndex(lessons, requestedId, lastFeaturedId, randomFn) {
    if (!Array.isArray(lessons) || !lessons.length) {
      return -1;
    }
    if (requestedId) {
      const requestedIndex = lessons.findIndex((lesson) => lesson && lesson.id === requestedId);
      if (requestedIndex >= 0) {
        return requestedIndex;
      }
    }
    const lastIndex = lastFeaturedId
      ? lessons.findIndex((lesson) => lesson && lesson.id === lastFeaturedId)
      : -1;
    return pickRandomIndex(lessons.length, lastIndex, randomFn);
  }

  function hasActiveFilters(filters) {
    if (!filters) {
      return false;
    }
    const query = typeof filters.query === "string" ? filters.query.trim() : "";
    return Boolean(query || filters.category || filters.level);
  }

  /**
   * Appends a freshly fetched page to the summaries already on screen. A
   * lesson can arrive twice when a deep-linked pick was inserted before its
   * page was loaded, so duplicate ids are skipped.
   */
  function mergeLessonSummaries(existing, incoming) {
    const merged = Array.isArray(existing) ? existing.slice() : [];
    const seen = new Set();
    merged.forEach((lesson) => {
      if (lesson && lesson.id) {
        seen.add(lesson.id);
      }
    });
    (Array.isArray(incoming) ? incoming : []).forEach((lesson) => {
      if (!lesson || !lesson.id || seen.has(lesson.id)) {
        return;
      }
      seen.add(lesson.id);
      merged.push(lesson);
    });
    return merged;
  }

  function formatListeningProgress(percent) {
    if (percent >= 99.5) {
      return "已完成";
    }
    return percent > 0 ? "已听 " + Math.max(1, Math.round(percent)) + "%" : "尚未开始";
  }

  function matchesCategory(category, selectedCategory) {
    const selected = selectedCategory.trim().replace(/\s+/g, " ").toLowerCase();
    return !selected || category.trim().replace(/\s+/g, " ").toLowerCase() === selected;
  }

  function buildLibraryHash(filters) {
    const params = new URLSearchParams();
    ["category", "level", "query"].forEach((key) => {
      if (filters[key]) {
        params.set(key, filters[key]);
      }
    });
    const query = params.toString();
    return "#library" + (query ? "?" + query : "");
  }

  function parseLibraryHash(hash) {
    if (hash !== "#library" && !hash.startsWith("#library?")) {
      return null;
    }
    const params = new URLSearchParams(hash.slice("#library?".length));
    return {
      category: params.get("category") || "",
      level: params.get("level") || "",
      query: params.get("query") || ""
    };
  }

  return {
    formatListeningProgress: formatListeningProgress,
    matchesCategory: matchesCategory,
    buildLibraryHash: buildLibraryHash,
    parseLibraryHash: parseLibraryHash,
    pickRandomIndex: pickRandomIndex,
    resolveInitialIndex: resolveInitialIndex,
    hasActiveFilters: hasActiveFilters,
    mergeLessonSummaries: mergeLessonSummaries
  };
});
