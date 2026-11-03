package com.xiaojie.englishlearning.translation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationStatusServiceTest {

    @TempDir
    Path tempDir;

    private LessonStore store;
    private TranslationState translationState;
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        StoragePaths paths = new StoragePaths(tempDir.toString());
        store = new LessonStore(paths, new ObjectMapper());
        translationState = new TranslationState(paths, new ObjectMapper());
        translationService = new TranslationService(sentences -> sentences, true, 1000);
    }

    @Test
    void reportsAnEmptyLibrary() {
        TranslationStatus status = statusService().getStatus();

        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getProvider()).isEqualTo("deepseek");
        assertThat(status.getModel()).isEqualTo("deepseek-chat");
        assertThat(status.isStateCurrent()).isFalse();
        assertThat(status.getStoredProvider()).isNull();
        assertThat(status.getStoredVersion()).isNull();
        assertThat(status.getTotalLessons()).isZero();
        assertThat(status.getTotalLines()).isZero();
        assertThat(status.getTranslatedLines()).isZero();
        assertThat(status.getMissingLines()).isZero();
        assertThat(status.getTranslatedLessons()).isZero();
        assertThat(status.getMissingLessons()).isZero();
    }

    @Test
    void countsTranslatedAndMissingLines() throws IOException {
        insertLesson("one", Arrays.asList(line("Hello.", "你好。"), line("World.", null)));

        TranslationStatus status = statusService().getStatus();

        assertThat(status.getTotalLessons()).isEqualTo(1);
        assertThat(status.getTotalLines()).isEqualTo(2);
        assertThat(status.getTranslatedLines()).isEqualTo(1);
        assertThat(status.getMissingLines()).isEqualTo(1);
        assertThat(status.getTranslatedLessons()).isZero();
        assertThat(status.getMissingLessons()).isEqualTo(1);
    }

    @Test
    void countsFullyTranslatedLessons() throws IOException {
        insertLesson("one", Arrays.asList(line("Hello.", "你好。"), line("World.", "世界。")));
        insertLesson("two", Arrays.asList(line("Another.", null)));

        TranslationStatus status = statusService().getStatus();

        assertThat(status.getTotalLessons()).isEqualTo(2);
        assertThat(status.getTotalLines()).isEqualTo(3);
        assertThat(status.getTranslatedLines()).isEqualTo(2);
        assertThat(status.getMissingLines()).isEqualTo(1);
        assertThat(status.getTranslatedLessons()).isEqualTo(1);
        assertThat(status.getMissingLessons()).isEqualTo(1);
    }

    @Test
    void reportsTheStoredMarkerAfterTheMigrationRuns() throws IOException {
        translationState.markCurrent();

        TranslationStatus status = statusService().getStatus();

        assertThat(status.isStateCurrent()).isTrue();
        assertThat(status.getStoredProvider()).isEqualTo("deepseek");
        assertThat(status.getStoredVersion()).isEqualTo(1);
    }

    @Test
    void reportsWhenTranslationIsDisabled() {
        TranslationStatus status = new TranslationStatusService(
                new TranslationService(sentences -> sentences, false, 1000),
                translationState, store, "deepseek-chat").getStatus();

        assertThat(status.isEnabled()).isFalse();
    }

    private TranslationStatusService statusService() {
        return new TranslationStatusService(translationService, translationState, store,
                "deepseek-chat");
    }

    private void insertLesson(String id, java.util.List<TranscriptLine> lines) throws IOException {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTitle("Lesson " + id);
        lesson.setNumber(id);
        lesson.setTranscript(lines);
        store.insert(lesson);
    }

    private TranscriptLine line(String text, String translation) {
        TranscriptLine line = new TranscriptLine();
        line.setText(text);
        line.setTranslation(translation);
        return line;
    }
}
