package com.xiaojie.englishlearning.lesson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class Lesson {

    private String id;
    private String number;
    private String title;
    private String titleZh;
    private String category;
    private String level;
    private double duration;
    private String audio;
    private String cover;
    private String summary;
    private String summaryZh;
    private String updatedAt;
    private List<TranscriptLine> transcript = new ArrayList<TranscriptLine>();
    private String wordsText;
    private List<KeyPhrase> keyPhrases = new ArrayList<KeyPhrase>();
    @JsonIgnore
    private Long persistenceVersion;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleZh() {
        return titleZh;
    }

    public void setTitleZh(String titleZh) {
        this.titleZh = titleZh;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getAudio() {
        return audio;
    }

    public void setAudio(String audio) {
        this.audio = audio;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryZh() {
        return summaryZh;
    }

    public void setSummaryZh(String summaryZh) {
        this.summaryZh = summaryZh;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TranscriptLine> getTranscript() {
        return transcript;
    }

    public void setTranscript(List<TranscriptLine> transcript) {
        this.transcript = transcript;
    }

    public String getWordsText() {
        return wordsText;
    }

    public void setWordsText(String wordsText) {
        this.wordsText = wordsText;
    }

    public List<KeyPhrase> getKeyPhrases() {
        return keyPhrases;
    }

    public void setKeyPhrases(List<KeyPhrase> keyPhrases) {
        this.keyPhrases = keyPhrases;
    }

    @JsonIgnore
    public Long getPersistenceVersion() {
        return persistenceVersion;
    }

    @JsonIgnore
    public void setPersistenceVersion(Long persistenceVersion) {
        this.persistenceVersion = persistenceVersion;
    }
}
