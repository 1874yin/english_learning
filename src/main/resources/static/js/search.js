(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.CadenceSearch = api;
  }
})(typeof window === "object" ? window : null, function () {
  "use strict";

  // Removed rather than replaced with a space, so "china's" normalizes to "chinas".
  const APOSTROPHES = /['’]/g;
  // Diacritic marks left over after NFKD decomposition (U+0300-U+036F).
  const COMBINING_MARKS = /[̀-ͯ]/g;
  // ASCII punctuation; replaced with a space so "daily-life" splits into words.
  const ASCII_PUNCTUATION = /[!-\/:-@\[-`{-~]/g;
  // Full-width/CJK punctuation and typographic quotes (U+3000-U+303F, U+FF01-U+FF40, etc.).
  const CJK_PUNCTUATION = /[　、-〿！-｀～·–—‘-”…]/g;
  const WHITESPACE = /\s+/g;

  /**
   * Lowercase, strip diacritics and punctuation, and collapse whitespace so
   * that queries like "china's" or "daily-life" match their plain spellings.
   */
  function normalizeSearchText(value) {
    if (typeof value !== "string") {
      return "";
    }
    return value
      .normalize("NFKD")
      .replace(COMBINING_MARKS, "")
      .toLowerCase()
      .replace(APOSTROPHES, "")
      .replace(ASCII_PUNCTUATION, " ")
      .replace(CJK_PUNCTUATION, " ")
      .replace(WHITESPACE, " ")
      .trim();
  }

  function searchTokens(query) {
    const normalized = normalizeSearchText(query);
    return normalized ? normalized.split(" ") : [];
  }

  /** Every query word must appear in the English or Chinese title, in any order. */
  function matchesLessonSearch(lesson, query) {
    const tokens = searchTokens(query);
    if (!tokens.length) {
      return true;
    }
    const title = lesson && typeof lesson.title === "string" ? lesson.title : "";
    const titleZh = lesson && typeof lesson.titleZh === "string" ? lesson.titleZh : "";
    const haystack = normalizeSearchText(title + " " + titleZh);
    return tokens.every((token) => haystack.indexOf(token) !== -1);
  }

  return {
    normalizeSearchText: normalizeSearchText,
    searchTokens: searchTokens,
    matchesLessonSearch: matchesLessonSearch
  };
});
