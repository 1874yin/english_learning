package com.xiaojie.englishlearning.lesson;

import java.util.List;

/**
 * One page of lesson summaries. {@code total} is the size of the whole
 * library, independent of the current page.
 */
public class LessonPage {

    private final List<LessonSummary> items;
    private final int page;
    private final int size;
    private final int total;
    private final boolean hasMore;

    public LessonPage(List<LessonSummary> items, int page, int size, int total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.hasMore = (long) (page + 1) * size < total;
    }

    public List<LessonSummary> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotal() {
        return total;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
