package com.xiaojie.englishlearning.user;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class ListeningData {
    public Map<String, Record> records = new LinkedHashMap<>();
    public Map<String, Stream> streams = new LinkedHashMap<>();
    public TreeMap<String, Double> daily = new TreeMap<>();
    public Map<String, Double> hours = new TreeMap<>();
    public Map<String, Long> favorites = new LinkedHashMap<>();
    public String lastCreditedAt;
    public JsonNode analysis;

    public static class Record {
        public String lessonId;
        public String title;
        public String titleZh;
        public String category;
        public double duration;
        public double position;
        public double listenedSeconds;
        public long updatedAt;
        public String lastListenedAt;
        public boolean imported;
    }

    public static class Stream {
        public String lessonId;
        public int sequence;
        public double seconds;
        public String lastReportedAt;
    }
}
