package com.xiaojie.englishlearning.admin;

import com.xiaojie.englishlearning.lesson.Lesson;

public class AdminLessonSummary {

    private final String id;
    private final String number;
    private final String title;
    private final String titleZh;
    private final String category;
    private final String level;
    private final String cover;
    private final double duration;
    private final int sentenceCount;
    private final String updatedAt;

    private AdminLessonSummary(Lesson lesson) {
        this.id = lesson.getId();
        this.number = lesson.getNumber();
        this.title = lesson.getTitle();
        this.titleZh = lesson.getTitleZh();
        this.category = lesson.getCategory();
        this.level = lesson.getLevel();
        this.cover = lesson.getCover();
        this.duration = lesson.getDuration();
        this.sentenceCount = lesson.getTranscript() == null ? 0 : lesson.getTranscript().size();
        this.updatedAt = lesson.getUpdatedAt();
    }

    public static AdminLessonSummary from(Lesson lesson) {
        return new AdminLessonSummary(lesson);
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

    public String getCover() {
        return cover;
    }

    public double getDuration() {
        return duration;
    }

    public int getSentenceCount() {
        return sentenceCount;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
