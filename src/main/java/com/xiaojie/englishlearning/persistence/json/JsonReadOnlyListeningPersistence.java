package com.xiaojie.englishlearning.persistence.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.persistence.ListeningPersistence;
import com.xiaojie.englishlearning.persistence.ReadOnlyPersistenceException;
import com.xiaojie.englishlearning.storage.StoragePaths;
import com.xiaojie.englishlearning.user.ListeningData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "json-read-only")
public class JsonReadOnlyListeningPersistence implements ListeningPersistence {
    private final Path directory;
    private final ObjectMapper mapper;

    public JsonReadOnlyListeningPersistence(StoragePaths paths, ObjectMapper mapper) {
        this.directory = paths.getRoot().resolve("user-data").normalize();
        this.mapper = mapper;
    }

    @Override
    public ListeningData load(String userId) {
        if (userId == null || userId.contains("/") || userId.contains("\\")) return new ListeningData();
        Path file = directory.resolve(userId + ".json").normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) return new ListeningData();
        try {
            ListeningData data = mapper.readValue(file.toFile(), ListeningData.class);
            if (data == null) return new ListeningData();
            if (data.records == null) data.records = new java.util.LinkedHashMap<>();
            if (data.streams == null) data.streams = new java.util.LinkedHashMap<>();
            if (data.daily == null) data.daily = new java.util.TreeMap<>();
            if (data.hours == null) data.hours = new java.util.TreeMap<>();
            if (data.favorites == null) data.favorites = new java.util.LinkedHashMap<>();
            return data;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read listening snapshot " + file, ex);
        }
    }

    @Override
    public <T> T update(String userId, Function<ListeningData, T> operation) {
        throw new ReadOnlyPersistenceException();
    }

    @Override public void checkWritable() { throw new ReadOnlyPersistenceException(); }
}
