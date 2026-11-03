package com.xiaojie.englishlearning.lesson;

/**
 * The projection returned by the public lesson list. It only contains the
 * fields the playlist renders; transcripts, summaries and words are fetched
 * from {@code GET /api/lessons/{id}} when a lesson is opened.
 */
public class LessonSummary {

    private final String id;
    private final String number;
    private final String title;
    private final String titleZh;
    private final String category;
    private final String level;
    private final double duration;
    private final String cover;

    public LessonSummary(String id, String number, String title, String titleZh,
            String category, String level, double duration, String cover) {
        this.id = id;
        this.number = number;
        this.title = title;
        this.titleZh = titleZh;
        this.category = category;
        this.level = level;
        this.duration = duration;
        this.cover = cover;
    }

    public static LessonSummary from(Lesson lesson) {
        return new LessonSummary(
                lesson.getId(),
                lesson.getNumber(),
                lesson.getTitle(),
                lesson.getTitleZh(),
                lesson.getCategory(),
                lesson.getLevel(),
                lesson.getDuration(),
                lesson.getCover());
    }

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getTitleZh() {
        return titleZh;
    }

    public String getCategory() {
        return category;
    }

    public String getLevel() {
        return level;
    }

    public double getDuration() {
        return duration;
    }

    public String getCover() {
        return cover;
    }
}
