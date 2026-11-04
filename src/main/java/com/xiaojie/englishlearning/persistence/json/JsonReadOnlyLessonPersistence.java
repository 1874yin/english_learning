package com.xiaojie.englishlearning.persistence.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonPage;
import com.xiaojie.englishlearning.lesson.LessonSummary;
import com.xiaojie.englishlearning.persistence.LessonPersistence;
import com.xiaojie.englishlearning.persistence.ReadOnlyPersistenceException;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "json-read-only")
public class JsonReadOnlyLessonPersistence implements LessonPersistence {
    private final StoragePaths paths;
    private final ObjectMapper mapper;

    public JsonReadOnlyLessonPersistence(StoragePaths paths, ObjectMapper mapper) {
        this.paths = paths;
        this.mapper = mapper;
    }

    @Override
    public List<Lesson> findAll() {
        if (!Files.exists(paths.getLessonsFile())) return new ArrayList<>();
        try {
            List<Lesson> lessons = mapper.readValue(paths.getLessonsFile().toFile(), new TypeReference<List<Lesson>>() { });
            if (lessons == null) return new ArrayList<>();
            for (Lesson lesson : lessons) {
                if (lesson == null || lesson.getId() == null || lesson.getId().isBlank()
                        || lesson.getTitle() == null || lesson.getTitle().isBlank()) {
                    throw new IllegalStateException("Lesson snapshot contains an entry without an id or title.");
                }
                if (lesson.getTranscript() == null) lesson.setTranscript(new ArrayList<>());
                if (lesson.getKeyPhrases() == null) lesson.setKeyPhrases(new ArrayList<>());
            }
            return lessons;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read lesson snapshot " + paths.getLessonsFile(), ex);
        }
    }

    @Override
    public Optional<Lesson> findById(String id) {
        return findAll().stream().filter(lesson -> lesson.getId().equals(id)).findFirst();
    }

    @Override
    public String nextNumber() {
        int max = 0;
        for (Lesson lesson : findAll()) {
            try { max = Math.max(max, Integer.parseInt(lesson.getNumber())); }
            catch (NumberFormatException ignored) { }
        }
        return String.format(Locale.ROOT, "%02d", max + 1);
    }

    @Override public Lesson insert(Lesson lesson) { throw new ReadOnlyPersistenceException(); }
    @Override public Lesson replace(Lesson lesson) { throw new ReadOnlyPersistenceException(); }
    @Override public boolean updateTranslations(String id, List<String> expectedTexts, List<String> translations) {
        throw new ReadOnlyPersistenceException();
    }
    @Override public int clearAllTranslations() { throw new ReadOnlyPersistenceException(); }

    @Override
    public LessonPage findPage(int page, int size) {
        List<Lesson> all = findAll();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        long offset = (long) safePage * safeSize;
        List<LessonSummary> summaries = new ArrayList<>();
        if (offset < all.size()) {
            for (Lesson lesson : all.subList((int) offset, Math.min(all.size(), (int) offset + safeSize))) {
                summaries.add(LessonSummary.from(lesson));
            }
        }
        return new LessonPage(summaries, safePage, safeSize, all.size());
    }

    @Override public void checkWritable() { throw new ReadOnlyPersistenceException(); }
}
