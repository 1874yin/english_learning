const test = require("node:test");
const assert = require("node:assert/strict");
const { matchesLessonSearch } = require("../../main/resources/static/js/search.js");

const lesson = { title: "Life in a Small Town", titleZh: "小镇生活" };

test("empty query matches every lesson", () => {
  assert.equal(matchesLessonSearch(lesson, ""), true);
  assert.equal(matchesLessonSearch(lesson, "   "), true);
});

test("matches an English title case-insensitively", () => {
  assert.equal(matchesLessonSearch(lesson, "SMALL"), true);
});

test("matches title substrings", () => {
  assert.equal(matchesLessonSearch(lesson, "sma"), true);
});

test("matches a Chinese title", () => {
  assert.equal(matchesLessonSearch(lesson, "生活"), true);
});

test("requires every word but ignores their order", () => {
  assert.equal(matchesLessonSearch(lesson, "town small"), true);
  assert.equal(matchesLessonSearch(lesson, "small ocean"), false);
});

test("matches words across English and Chinese titles", () => {
  assert.equal(matchesLessonSearch(lesson, "town 生活"), true);
});

test("ignores punctuation and diacritics", () => {
  assert.equal(matchesLessonSearch({ title: "Café Society" }, "cafe"), true);
  assert.equal(matchesLessonSearch({ title: "China's Rivers" }, "china's"), true);
  assert.equal(matchesLessonSearch({ title: "A Daily-Life Story" }, "daily life"), true);
});

test("treats a punctuation-only query as empty", () => {
  assert.equal(matchesLessonSearch(lesson, "!!!"), true);
});

test("handles lessons without titles", () => {
  assert.equal(matchesLessonSearch({}, "life"), false);
  assert.equal(matchesLessonSearch(null, ""), true);
});
