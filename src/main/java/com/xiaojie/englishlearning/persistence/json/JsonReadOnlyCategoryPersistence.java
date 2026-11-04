package com.xiaojie.englishlearning.persistence.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.persistence.CategoryPersistence;
import com.xiaojie.englishlearning.persistence.ReadOnlyPersistenceException;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "json-read-only")
public class JsonReadOnlyCategoryPersistence implements CategoryPersistence {
    private final StoragePaths paths;
    private final ObjectMapper mapper;

    public JsonReadOnlyCategoryPersistence(StoragePaths paths, ObjectMapper mapper) {
        this.paths = paths;
        this.mapper = mapper;
    }

    @Override
    public List<String> findAll() {
        if (!Files.exists(paths.getCategoriesFile())) return new ArrayList<>();
        try {
            List<String> names = mapper.readValue(paths.getCategoriesFile().toFile(), new TypeReference<List<String>>() { });
            if (names == null) return new ArrayList<>();
            List<String> normalized = new ArrayList<>();
            java.util.Set<String> seen = new LinkedHashSet<>();
            for (String raw : names) {
                if (raw == null) continue;
                String name = raw.trim().replaceAll("\\s+", " ");
                if (!name.isEmpty() && seen.add(name.toLowerCase(Locale.ROOT))) normalized.add(name);
            }
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read category snapshot " + paths.getCategoriesFile(), ex);
        }
    }

    @Override public String add(String name, List<String> defaults) { throw new ReadOnlyPersistenceException(); }
    @Override public boolean remove(String name, List<String> defaults) { throw new ReadOnlyPersistenceException(); }
    @Override public void checkWritable() { throw new ReadOnlyPersistenceException(); }
}
