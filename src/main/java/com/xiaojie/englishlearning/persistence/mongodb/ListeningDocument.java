package com.xiaojie.englishlearning.persistence.mongodb;

import com.xiaojie.englishlearning.user.ListeningData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_listening")
public class ListeningDocument {
    @Id
    private String id;
    @Version
    private Long version;
    private Map<String, ListeningData.Record> records = new LinkedHashMap<>();
    private Map<String, ListeningData.Stream> streams = new LinkedHashMap<>();
    private Map<String, Double> daily = new LinkedHashMap<>();
    private Map<String, Double> hours = new LinkedHashMap<>();
    private Map<String, Long> favorites = new LinkedHashMap<>();
    private String lastCreditedAt;
    private Object analysis;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Map<String, ListeningData.Record> getRecords() { return records; }
    public void setRecords(Map<String, ListeningData.Record> records) { this.records = records; }
    public Map<String, ListeningData.Stream> getStreams() { return streams; }
    public void setStreams(Map<String, ListeningData.Stream> streams) { this.streams = streams; }
    public Map<String, Double> getDaily() { return daily; }
    public void setDaily(Map<String, Double> daily) { this.daily = daily; }
    public Map<String, Double> getHours() { return hours; }
    public void setHours(Map<String, Double> hours) { this.hours = hours; }
    public Map<String, Long> getFavorites() { return favorites; }
    public void setFavorites(Map<String, Long> favorites) { this.favorites = favorites; }
    public String getLastCreditedAt() { return lastCreditedAt; }
    public void setLastCreditedAt(String lastCreditedAt) { this.lastCreditedAt = lastCreditedAt; }
    public Object getAnalysis() { return analysis; }
    public void setAnalysis(Object analysis) { this.analysis = analysis; }
}
