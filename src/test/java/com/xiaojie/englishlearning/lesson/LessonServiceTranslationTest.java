package com.xiaojie.englishlearning.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.storage.StoragePaths;
import com.xiaojie.englishlearning.translation.TranslationException;
import com.xiaojie.englishlearning.translation.TranslationProvider;
import com.xiaojie.englishlearning.translation.TranslationService;
import com.xiaojie.englishlearning.translation.TranslationState;
import com.xiaojie.englishlearning.translation.TranslationUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class LessonServiceTranslationTest {

    @TempDir
    Path tempDir;

    private LessonStore store;
    private TranscriptProcessor transcriptProcessor;
    private TranslationState translationState;
    private CountingProvider provider;

    @BeforeEach
    void setUp() {
        StoragePaths paths = new StoragePaths(tempDir.toString());
        store = new LessonStore(paths, new ObjectMapper());
        store.load();
        transcriptProcessor = new TranscriptProcessor();
        translationState = new TranslationState(paths, new ObjectMapper());
        provider = new CountingProvider();
    }

    @Test
    void createsALessonWithChineseTranslationsFilledIn() throws IOException {
        LessonService service = serviceWithTranslation();

        Lesson lesson = service.create(input("Hello world. This is a test."), audio(), null);

        assertThat(lesson.getTranscript()).hasSize(2);
        assertThat(lesson.getTranscript().get(0).getTranslation()).isEqualTo("译文1。");
        assertThat(lesson.getTranscript().get(1).getTranslation()).isEqualTo("译文2。");
        assertThat(provider.calls.get()).isEqualTo(1);
    }

    @Test
    void metadataOnlyUpdateKeepsExistingTranslations() throws IOException {
        LessonService service = serviceWithTranslation();
        Lesson lesson = service.create(input("Hello world. This is a test."), audio(), null);

        Lesson updated = service.update(lesson.getId(), input("Hello world. This is a test."), null, null);

        assertThat(updated.getTranscript().get(0).getTranslation()).isEqualTo("译文1。");
        assertThat(updated.getTranscript().get(1).getTranslation()).isEqualTo("译文2。");
        assertThat(provider.calls.get()).isEqualTo(1);
    }

    @Test
    void changedTranscriptIsTranslatedAgain() throws IOException {
        LessonService service = serviceWithTranslation();
        Lesson lesson = service.create(input("Hello world. This is a test."), audio(), null);

        Lesson updated = service.update(lesson.getId(),
                input("One new sentence. A second new sentence."), null, null);

        assertThat(updated.getTranscript()).hasSize(2);
        assertThat(updated.getTranscript().get(0).getTranslation()).isEqualTo("译文1。");
        assertThat(updated.getTranscript().get(1).getTranslation()).isEqualTo("译文2。");
        assertThat(provider.calls.get()).isEqualTo(2);
    }

    @Test
    void translationFailureDoesNotPreventSavingTheLesson() throws IOException {
        TranslationService translationService = new TranslationService(
                failingProvider(), true, 1000);
        LessonService service = new LessonService(store, transcriptProcessor, translationService,
                translationState);

        Lesson lesson = service.create(input("Hello world. This is a test."), audio(), null);

        assertThat(lesson.getId()).isNotEmpty();
        assertThat(lesson.getTranscript()).hasSize(2);
        assertThat(lesson.getTranscript().get(0).getTranslation()).isNull();
    }

    @Test
    void translatesExistingLessonsThatAreMissingChinese() throws IOException {
        Lesson created = createWithoutTranslation("Hello world. This is a test.");

        LessonService service = serviceWithTranslation();
        int translated = service.translateMissingLessons();

        assertThat(translated).isEqualTo(1);
        Lesson stored = store.findById(created.getId()).get();
        assertThat(stored.getTranscript().get(0).getTranslation()).isEqualTo("译文1。");
        assertThat(stored.getTranscript().get(1).getTranslation()).isEqualTo("译文2。");
    }

    @Test
    void clearsLegacyTranslationsBeforeFillingThemAgain() throws IOException {
        Lesson created = createWithoutTranslation("Hello world. This is a test.");
        setLegacyTranslations(created);

        LessonService service = serviceWithTranslation();
        int translated = service.translateMissingLessons();

        assertThat(translated).isEqualTo(1);
        Lesson stored = store.findById(created.getId()).get();
        assertThat(stored.getTranscript().get(0).getTranslation()).isEqualTo("译文1。");
        assertThat(stored.getTranscript().get(1).getTranslation()).isEqualTo("译文2。");
        assertThat(translationState.isCurrent()).isTrue();
    }

    @Test
    void keepsTranslationsOnceTheProviderMarkerIsCurrent() throws IOException {
        translationState.markCurrent();
        Lesson created = createWithoutTranslation("Hello world. This is a test.");
        setLegacyTranslations(created);

        LessonService service = serviceWithTranslation();
        int translated = service.translateMissingLessons();

        assertThat(translated).isZero();
        Lesson stored = store.findById(created.getId()).get();
        assertThat(stored.getTranscript().get(0).getTranslation()).isEqualTo("旧译文1。");
        assertThat(stored.getTranscript().get(1).getTranslation()).isEqualTo("旧译文2。");
        assertThat(provider.calls.get()).isZero();
    }

    @Test
    void doesNotClearAnythingWhenInterruptedBeforeStarting() throws IOException {
        Lesson created = createWithoutTranslation("Hello world. This is a test.");
        setLegacyTranslations(created);

        LessonService service = serviceWithTranslation();
        Thread.currentThread().interrupt();
        try {
            assertThat(service.translateMissingLessons()).isZero();
        } finally {
            Thread.interrupted();
        }

        Lesson stored = store.findById(created.getId()).get();
        assertThat(stored.getTranscript().get(0).getTranslation()).isEqualTo("旧译文1。");
        assertThat(translationState.isCurrent()).isFalse();
    }

    @Test
    void stopsTheScanWhenTheTranslationProviderIsUnavailable() throws IOException {
        createWithoutTranslation("Hello world. This is a test.");
        createWithoutTranslation("Another sentence. And one more.");

        AtomicInteger calls = new AtomicInteger();
        TranslationProvider unavailable = sentences -> {
            calls.incrementAndGet();
            throw new TranslationUnavailableException("DeepSeek returned HTTP 401");
        };
        LessonService service = new LessonService(store, transcriptProcessor,
                new TranslationService(unavailable, true, 1000), translationState);

        int translated = service.translateMissingLessons();

        assertThat(translated).isZero();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void refusesToApplyTranslationsToAChangedTranscript() throws IOException {
        Lesson created = createWithoutTranslation("Hello world. This is a test.");

        assertThat(store.updateTranslations(created.getId(),
                Arrays.asList("Hello world.", "This is a test."),
                Arrays.asList("甲。", "乙。"))).isTrue();

        assertThat(store.updateTranslations(created.getId(),
                Arrays.asList("Something else.", "This is a test."),
                Arrays.asList("丙。", "丁。"))).isFalse();
        assertThat(store.findById(created.getId()).get().getTranscript().get(0).getTranslation())
                .isEqualTo("甲。");
    }

    private Lesson createWithoutTranslation(String transcript) throws IOException {
        LessonService withoutTranslation = new LessonService(store, transcriptProcessor,
                new TranslationService(provider, false, 1000), translationState);
        return withoutTranslation.create(input(transcript), audio(), null);
    }

    private void setLegacyTranslations(Lesson lesson) throws IOException {
        assertThat(store.updateTranslations(lesson.getId(),
                Arrays.asList("Hello world.", "This is a test."),
                Arrays.asList("旧译文1。", "旧译文2。"))).isTrue();
    }

    private LessonService serviceWithTranslation() {
        TranslationService translationService = new TranslationService(provider, true, 1000);
        return new LessonService(store, transcriptProcessor, translationService, translationState);
    }

    private LessonInput input(String transcript) {
        LessonInput input = new LessonInput();
        input.setTitle("A test lesson");
        input.setTranscriptText(transcript);
        return input;
    }

    private MockMultipartFile audio() throws IOException {
        return new MockMultipartFile("audio", "sample.mp3", "audio/mpeg",
                new ClassPathResource("fixtures/sample.mp3").getInputStream());
    }

    private TranslationProvider failingProvider() {
        return sentences -> {
            throw new TranslationException("provider failed");
        };
    }

    private static final class CountingProvider implements TranslationProvider {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<List<String>> inputs = new ArrayList<List<String>>();

        @Override
        public List<String> translate(List<String> sentences) {
            calls.incrementAndGet();
            inputs.add(new ArrayList<String>(sentences));
            List<String> result = new ArrayList<String>(sentences.size());
            for (int i = 0; i < sentences.size(); i += 1) {
                result.add("译文" + (i + 1) + "。");
            }
            return result;
        }
    }
}
