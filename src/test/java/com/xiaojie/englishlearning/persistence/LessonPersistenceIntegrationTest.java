package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaojie.englishlearning.lesson.KeyPhrase;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonConflictException;
import com.xiaojie.englishlearning.lesson.LessonPage;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"app.translation.enabled=false", "spring.flyway.enabled=true"})
class LessonPersistenceIntegrationTest extends DatabaseTestContainers {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
    }

    @Autowired
    private LessonStore lessons;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM lessons");
        jdbcTemplate.update("UPDATE lesson_number_sequence SET next_number = 1, next_sort_order = 1 WHERE id = 1");
    }

    @Test
    void storesAndReloadsCompleteLessonInMysql() throws Exception {
        Lesson lesson = lesson("lesson-test");

        lessons.insert(lesson);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons WHERE id = ?", Integer.class,
                lesson.getId())).isEqualTo(1);
        Lesson loaded = lessons.findById(lesson.getId()).orElseThrow();
        assertThat(loaded.getNumber()).isEqualTo("01");
        assertThat(loaded.getTitle()).isEqualTo("Persistence test");
        assertThat(loaded.getSummary()).isNull();
        assertThat(loaded.getTranscript()).hasSize(1);
        assertThat(loaded.getTranscript().get(0).getText()).isEqualTo("First sentence.");
        assertThat(loaded.getKeyPhrases()).hasSize(1);
        assertThat(loaded.getKeyPhrases().get(0).getPhrase()).isEqualTo("first sentence");
    }

    @Test
    void staleConcurrentLessonUpdateReturnsConflictWithoutOverwritingNewerEdit() throws Exception {
        Lesson inserted = lessons.insert(lesson("lesson-concurrent"));
        Lesson firstRead = lessons.findById(inserted.getId()).orElseThrow();
        Lesson staleRead = lessons.findById(inserted.getId()).orElseThrow();

        firstRead.setTitle("Winning update");
        lessons.replace(firstRead);
        staleRead.setTitle("Stale update");

        assertThatThrownBy(() -> lessons.replace(staleRead))
                .isInstanceOf(LessonConflictException.class);
        assertThat(lessons.findById(inserted.getId()).orElseThrow().getTitle()).isEqualTo("Winning update");
    }

    @Test
    void lessonSummaryPaginationUsesStableInsertionOrderIndex() throws Exception {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(new Object[] {"lesson-page-" + i, String.format("%02d", i + 1), i + 1L,
                    "Page lesson " + i, "Daily Life", "Beginner", 12.5, "2026-11-03T00:00:00Z", "[]", "[]", 0L});
        }
        jdbcTemplate.batchUpdate("INSERT INTO lessons "
                + "(id, number, sort_order, title, category, level, duration, updated_at, transcript, key_phrases, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);

        LessonPage page = lessons.findPage(0, 24);
        assertThat(page.getTotal()).isEqualTo(1000);
        assertThat(page.getItems()).hasSize(24);
        assertThat(page.getItems().get(0).getTitle()).isEqualTo("Page lesson 0");
        assertThat(page.isHasMore()).isTrue();
        var explain = jdbcTemplate.queryForMap("EXPLAIN SELECT id, number, title, title_zh, category, level, duration, cover "
                + "FROM lessons FORCE INDEX (uk_lessons_sort_order) ORDER BY sort_order LIMIT 0, 24");
        assertThat(explain.get("key")).as("pagination query plan: %s", explain)
                .isEqualTo("uk_lessons_sort_order");
    }

    private static Lesson lesson(String id) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setNumber("01");
        lesson.setTitle("Persistence test");
        lesson.setCategory("Daily Life");
        lesson.setLevel("Beginner");
        lesson.setDuration(12.5);
        lesson.setAudio("/media/test.mp3");
        lesson.setSummary(null);
        lesson.setUpdatedAt("2026-11-03T00:00:00Z");
        TranscriptLine line = new TranscriptLine();
        line.setStart(0);
        line.setEnd(12.5);
        line.setText("First sentence.");
        line.setTranslation("第一句。");
        lesson.setTranscript(Arrays.asList(line));
        KeyPhrase phrase = new KeyPhrase();
        phrase.setPhrase("first sentence");
        phrase.setMeaning("第一句话");
        lesson.setKeyPhrases(Arrays.asList(phrase));
        return lesson;
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("cadence-mysql-lessons-");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
