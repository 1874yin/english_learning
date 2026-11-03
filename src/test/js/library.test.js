const test = require("node:test");
const assert = require("node:assert/strict");
const {
  pickRandomIndex,
  resolveInitialIndex,
  hasActiveFilters,
  mergeLessonSummaries,
  buildLibraryHash,
  parseLibraryHash,
  matchesCategory,
  formatListeningProgress
} = require("../../main/resources/static/js/library.js");

const lessons = [{ id: "a" }, { id: "b" }, { id: "c" }];

test("pickRandomIndex returns -1 for an empty library", () => {
  assert.equal(pickRandomIndex(0, -1, () => 0), -1);
});

test("pickRandomIndex returns 0 for a single lesson", () => {
  assert.equal(pickRandomIndex(1, 0, () => 0.9), 0);
});

test("pickRandomIndex uses the supplied random source", () => {
  assert.equal(pickRandomIndex(5, -1, () => 0.4), 2);
});

test("pickRandomIndex never returns the excluded index", () => {
  for (let r = 0; r < 1; r += 0.05) {
    assert.notEqual(pickRandomIndex(3, 1, () => r), 1);
  }
});

test("resolveInitialIndex prefers a valid requested id", () => {
  assert.equal(resolveInitialIndex(lessons, "b", "a", () => 0), 1);
});

test("resolveInitialIndex falls back to a random lesson for an unknown id", () => {
  assert.equal(resolveInitialIndex(lessons, "missing", "a", () => 0.5), 1);
});

test("resolveInitialIndex avoids repeating the last featured lesson", () => {
  assert.notEqual(resolveInitialIndex(lessons, "", "a", () => 0), 0);
});

test("resolveInitialIndex returns -1 for an empty library", () => {
  assert.equal(resolveInitialIndex([], "a", "b", () => 0), -1);
});

test("resolveInitialIndex returns 0 for a single lesson even if it was last", () => {
  assert.equal(resolveInitialIndex([{ id: "a" }], "", "a", () => 0.5), 0);
});

test("hasActiveFilters is false for empty or whitespace-only filters", () => {
  assert.equal(hasActiveFilters({ query: "", category: "", level: "" }), false);
  assert.equal(hasActiveFilters({ query: "   " }), false);
  assert.equal(hasActiveFilters(null), false);
});

test("hasActiveFilters is true for any active filter", () => {
  assert.equal(hasActiveFilters({ query: "morning" }), true);
  assert.equal(hasActiveFilters({ category: "Daily Life" }), true);
  assert.equal(hasActiveFilters({ level: "Beginner" }), true);
});

test("mergeLessonSummaries appends unseen lessons and drops duplicates", () => {
  const merged = mergeLessonSummaries(
    [{ id: "a" }, { id: "b" }],
    [{ id: "b" }, { id: "c" }, null, { id: "" }]
  );
  assert.deepEqual(merged.map((lesson) => lesson.id), ["a", "b", "c"]);
});

test("mergeLessonSummaries returns a copy when the page is empty", () => {
  const existing = [{ id: "a" }];
  const merged = mergeLessonSummaries(existing, null);
  assert.deepEqual(merged, existing);
  assert.notEqual(merged, existing);
});

test("listening progress distinguishes new, started and completed lessons", () => {
  assert.equal(formatListeningProgress(0), "尚未开始");
  assert.equal(formatListeningProgress(0.2), "已听 1%");
  assert.equal(formatListeningProgress(38.2), "已听 38%");
  assert.equal(formatListeningProgress(99.4), "已听 99%");
  assert.equal(formatListeningProgress(99.5), "已完成");
  assert.equal(formatListeningProgress(100), "已完成");
});

test("category filtering includes every spelling counted by the category cards", () => {
  const categories = ["browse test", "Browse   Test", "BROWSE TEST", "Other"];
  assert.equal(categories.filter((category) => matchesCategory(category, "Browse Test")).length, 3);
  assert.equal(categories.filter((category) => matchesCategory(category, " browse   TEST ")).length, 3);
  assert.equal(matchesCategory("Other", "Browse Test"), false);
  assert.equal(matchesCategory("Other", ""), true);
});

test("library links preserve the selected category and difficulty", () => {
  assert.equal(
    buildLibraryHash({ category: "Daily Life", level: "Beginner", query: "" }),
    "#library?category=Daily+Life&level=Beginner"
  );
  assert.deepEqual(parseLibraryHash("#library?category=Daily+Life&level=Beginner"), {
    category: "Daily Life", level: "Beginner", query: ""
  });
});

test("library links round trip non-ASCII categories, separators and search text", () => {
  const filters = { category: "新闻 & 科技/生活", level: "Advanced", query: "A&B? #1" };
  assert.deepEqual(parseLibraryHash(buildLibraryHash(filters)), filters);
});

test("the unfiltered library is distinct from the category homepage", () => {
  assert.equal(buildLibraryHash({ category: "", level: "", query: "" }), "#library");
  assert.deepEqual(parseLibraryHash("#library"), { category: "", level: "", query: "" });
  assert.equal(parseLibraryHash(""), null);
  assert.equal(parseLibraryHash("#lesson"), null);
});
