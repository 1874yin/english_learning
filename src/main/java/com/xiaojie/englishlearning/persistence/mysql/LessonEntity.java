package com.xiaojie.englishlearning.persistence.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xiaojie.englishlearning.lesson.KeyPhrase;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import com.xiaojie.englishlearning.persistence.mysql.type.KeyPhraseListTypeHandler;
import com.xiaojie.englishlearning.persistence.mysql.type.TranscriptListTypeHandler;
import java.util.List;

@TableName(value = "lessons", autoResultMap = true)
public class LessonEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String number;
    @TableField("sort_order")
    private Long sortOrder;
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
    @TableField(value = "transcript", typeHandler = TranscriptListTypeHandler.class)
    private List<TranscriptLine> transcript;
    private String wordsText;
    @TableField(value = "key_phrases", typeHandler = KeyPhraseListTypeHandler.class)
    private List<KeyPhrase> keyPhrases;
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Long getSortOrder() { return sortOrder; }
    public void setSortOrder(Long sortOrder) { this.sortOrder = sortOrder; }
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
    public String getAudio() { return audio; }
    public void setAudio(String audio) { this.audio = audio; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSummaryZh() { return summaryZh; }
    public void setSummaryZh(String summaryZh) { this.summaryZh = summaryZh; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<TranscriptLine> getTranscript() { return transcript; }
    public void setTranscript(List<TranscriptLine> transcript) { this.transcript = transcript; }
    public String getWordsText() { return wordsText; }
    public void setWordsText(String wordsText) { this.wordsText = wordsText; }
    public List<KeyPhrase> getKeyPhrases() { return keyPhrases; }
    public void setKeyPhrases(List<KeyPhrase> keyPhrases) { this.keyPhrases = keyPhrases; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
