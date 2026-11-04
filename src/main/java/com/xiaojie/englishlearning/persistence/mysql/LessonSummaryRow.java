package com.xiaojie.englishlearning.persistence.mysql;

public class LessonSummaryRow {
    private String id;
    private String number;
    private String title;
    private String titleZh;
    private String category;
    private String level;
    private double duration;
    private String cover;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
}
