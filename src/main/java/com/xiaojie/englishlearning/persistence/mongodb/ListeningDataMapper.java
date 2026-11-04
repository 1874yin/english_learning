package com.xiaojie.englishlearning.persistence.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.user.ListeningData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Component;

@Component
public class ListeningDataMapper {
    private final ObjectMapper mapper;
    private final MongoConverter mongoConverter;

    public ListeningDataMapper(ObjectMapper mapper, MongoConverter mongoConverter) {
        this.mapper = mapper;
        this.mongoConverter = mongoConverter;
    }

    public org.bson.Document toBsonDocument(String userId, ListeningData data) {
        org.bson.Document document = new org.bson.Document();
        mongoConverter.write(toDocument(userId, data, null), document);
        return document;
    }

    public ListeningData toDomain(ListeningDocument source) {
        ListeningData target = new ListeningData();
        if (source == null) return target;
        if (source.getRecords() != null) target.records.putAll(source.getRecords());
        if (source.getStreams() != null) target.streams.putAll(source.getStreams());
        if (source.getDaily() != null) target.daily.putAll(source.getDaily());
        if (source.getHours() != null) target.hours.putAll(source.getHours());
        if (source.getFavorites() != null) target.favorites.putAll(source.getFavorites());
        target.lastCreditedAt = source.getLastCreditedAt();
        if (source.getAnalysis() != null) {
            target.analysis = source.getAnalysis() instanceof JsonNode node
                    ? node.deepCopy() : mapper.valueToTree(source.getAnalysis());
        }
        return target;
    }

    public ListeningDocument toDocument(String userId, ListeningData source, Long version) {
        ListeningDocument target = new ListeningDocument();
        target.setId(userId);
        target.setVersion(version);
        target.setRecords(new LinkedHashMap<>(source.records));
        target.setStreams(new LinkedHashMap<>(source.streams));
        target.setDaily(new TreeMap<>(source.daily));
        target.setHours(new TreeMap<>(source.hours));
        target.setFavorites(new LinkedHashMap<>(source.favorites));
        target.setLastCreditedAt(source.lastCreditedAt);
        target.setAnalysis(source.analysis == null ? null : mapper.convertValue(source.analysis, Object.class));
        return target;
    }
}
