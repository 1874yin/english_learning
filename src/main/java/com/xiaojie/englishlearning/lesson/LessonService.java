package com.xiaojie.englishlearning.lesson;

import com.xiaojie.englishlearning.admin.AdminValidationException;
import com.xiaojie.englishlearning.translation.TranslationInterruptedException;
import com.xiaojie.englishlearning.translation.TranslationService;
import com.xiaojie.englishlearning.translation.TranslationState;
import com.xiaojie.englishlearning.translation.TranslationUnavailableException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LessonService {

    private static final Logger log = LoggerFactory.getLogger(LessonService.class);

    private static final String DEFAULT_COVER = "/images/cover-default.svg";
    private static final int MAX_WORDS_TEXT = 5000;
    private static final int MAX_PAGE_SIZE = 100;

    private final LessonStore store;
    private final TranscriptProcessor transcriptProcessor;
    private final TranslationService translationService;
    private final TranslationState translationState;

    public LessonService(LessonStore store, TranscriptProcessor transcriptProcessor,
            TranslationService translationService, TranslationState translationState) {
        this.store = store;
        this.transcriptProcessor = transcriptProcessor;
        this.translationService = translationService;
        this.translationState = translationState;
    }

    public List<Lesson> findAll() {
        return store.findAll();
    }

    /**
     * Returns one page of playlist-sized summaries. The public list endpoint
     * must stay cheap even when the library has thousands of subtitle lines,
     * so the full transcript is only sent by {@link #findById(String)}.
     */
    public LessonPage findPage(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);

        List<Lesson> lessons = store.findAll();
        int total = lessons.size();
        int from = (int) Math.min((long) safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<LessonSummary> items = new ArrayList<LessonSummary>(to - from);
        for (int i = from; i < to; i += 1) {
            items.add(LessonSummary.from(lessons.get(i)));
        }
        return new LessonPage(items, safePage, safeSize, total);
    }

    public Optional<Lesson> findById(String id) {
        return store.findById(id);
    }

    /**
     * Fills Chinese subtitles for stored lessons that do not have them yet.
     * Runs from the startup background task, so failures are logged per lesson
     * instead of stopping the scan. The task can be paused by interrupting its
     * thread; it stops between lessons and leaves the rest for the next startup.
     */
    public int translateMissingLessons() {
        if (!translationService.isEnabled()) {
            return 0;
        }
        if (Thread.currentThread().isInterrupted()) {
            log.info("Chinese translation task was cancelled before it started");
            return 0;
        }

        resetLegacyTranslations();

        int translated = 0;
        for (Lesson lesson : store.findAll()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Chinese translation paused; translated {} lesson(s) this run", translated);
                break;
            }

            List<TranscriptLine> lines = lesson.getTranscript();
            if (!needsTranslation(lines)) {
                continue;
            }

            List<String> sentences = transcriptTexts(lines);
            try {
                List<String> translations = translationService.translate(sentences);
                if (store.updateTranslations(lesson.getId(), sentences, translations)) {
                    translated += 1;
                }
            } catch (TranslationInterruptedException ex) {
                log.info("Chinese translation paused; translated {} lesson(s) this run", translated);
                break;
            } catch (TranslationUnavailableException ex) {
                log.warn("Chinese translation stopped for this run: {}. It will resume on the next startup.",
                        ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Chinese translation paused; translated {} lesson(s) this run", translated);
                    break;
                }
                log.warn("Could not translate lesson {}: {}", lesson.getId(), ex.getMessage());
            } catch (IOException ex) {
                log.warn("Could not save translations for lesson {}", lesson.getId(), ex);
            }
        }
        return translated;
    }

    /**
     * When the translation provider changes, subtitles from the previous
     * provider must not be kept. The marker file makes this a one-time reset so
     * a later restart does not wipe the new translations.
     */
    private void resetLegacyTranslations() {
        if (translationState.isCurrent()) {
            return;
        }
        try {
            int cleared = store.clearAllTranslations();
            translationState.markCurrent();
            if (cleared > 0) {
                log.info("Cleared {} Chinese subtitle line(s) left by the previous translation provider",
                        cleared);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not clear Chinese subtitles from the previous translation provider", ex);
        }
    }

    private void applyTranslations(List<TranscriptLine> lines) {
        if (!translationService.isEnabled() || lines == null || lines.isEmpty()) {
            return;
        }
        List<String> sentences = transcriptTexts(lines);
        try {
            List<String> translations = translationService.translate(sentences);
            if (translations.size() != lines.size()) {
                throw new IllegalStateException("Translation count did not match the transcript");
            }
            for (int i = 0; i < lines.size(); i += 1) {
                lines.get(i).setTranslation(translations.get(i));
            }
        } catch (RuntimeException ex) {
            log.warn("Automatic Chinese translation failed; it will be retried on the next startup: {}",
                    ex.getMessage());
        }
    }

    private boolean needsTranslation(List<TranscriptLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        for (TranscriptLine line : lines) {
            if (line == null || line.getText() == null || line.getTranslation() == null
                    || line.getTranslation().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<String> transcriptTexts(List<TranscriptLine> lines) {
        List<String> sentences = new ArrayList<String>(lines.size());
        for (TranscriptLine line : lines) {
            sentences.add(line.getText());
        }
        return sentences;
    }

    public Lesson create(LessonInput input, MultipartFile audio, MultipartFile cover) throws IOException {
        String title = requireTitle(input.getTitle());
        List<String> sentences = splitTranscript(input.getTranscriptText());
        String wordsText = resolveWordsText(input.getWordsText(), null);
        if (audio == null || audio.isEmpty()) {
            throw new AdminValidationException("请选择 MP3 音频文件。");
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        LessonStore.StoredAudio storedAudio = store.storeAudio(audio, id);
        LessonStore.StoredCover storedCover = null;
        try {
            if (cover != null && !cover.isEmpty()) {
                storedCover = store.storeCover(cover, id);
            }
            Lesson lesson = new Lesson();
            lesson.setId(id);
            lesson.setNumber(store.nextNumber());
            applyMetadata(lesson, input, title);
            lesson.setDuration(storedAudio.getDuration());
            lesson.setAudio(storedAudio.getUrl());
            lesson.setCover(storedCover == null ? DEFAULT_COVER : storedCover.getUrl());
            lesson.setUpdatedAt(Instant.now().toString());
            lesson.setTranscript(transcriptProcessor.estimateTimeline(sentences, storedAudio.getDuration()));
            applyTranslations(lesson.getTranscript());
            lesson.setWordsText(wordsText);
            lesson.setKeyPhrases(new ArrayList<KeyPhrase>());
            return store.insert(lesson);
        } catch (IOException | RuntimeException ex) {
            store.deleteAudio(storedAudio.getUrl());
            if (storedCover != null) {
                store.deleteCover(storedCover.getUrl());
            }
            throw ex;
        }
    }

    public Lesson update(String id, LessonInput input, MultipartFile audio, MultipartFile cover)
            throws IOException {
        Lesson existing = store.findById(id)
                .orElseThrow(() -> new NoSuchElementException("素材不存在：" + id));

        String title = requireTitle(input.getTitle());
        List<String> sentences = splitTranscript(input.getTranscriptText());

        LessonStore.StoredAudio storedAudio = null;
        double duration = existing.getDuration();
        String audioUrl = existing.getAudio();
        if (audio != null && !audio.isEmpty()) {
            storedAudio = store.storeAudio(audio, id);
            duration = storedAudio.getDuration();
            audioUrl = storedAudio.getUrl();
        }

        LessonStore.StoredCover storedCover = null;
        String coverUrl = existing.getCover() == null ? DEFAULT_COVER : existing.getCover();
        try {
            if (cover != null && !cover.isEmpty()) {
                storedCover = store.storeCover(cover, id);
                coverUrl = storedCover.getUrl();
            }

            Lesson lesson = new Lesson();
            lesson.setId(existing.getId());
            lesson.setNumber(existing.getNumber());
            applyMetadata(lesson, input, title);
            lesson.setDuration(duration);
            lesson.setAudio(audioUrl);
            lesson.setCover(coverUrl);
            lesson.setUpdatedAt(Instant.now().toString());
            lesson.setWordsText(resolveWordsText(input.getWordsText(), existing.getWordsText()));
            lesson.setKeyPhrases(existing.getKeyPhrases() == null
                    ? new ArrayList<KeyPhrase>()
                    : new ArrayList<KeyPhrase>(existing.getKeyPhrases()));

            boolean transcriptChanged = transcriptChanged(existing.getTranscript(), sentences);
            if (storedAudio != null || transcriptChanged) {
                lesson.setTranscript(transcriptProcessor.estimateTimeline(sentences, duration));
            } else {
                lesson.setTranscript(existing.getTranscript());
            }
            if (transcriptChanged) {
                applyTranslations(lesson.getTranscript());
            }

            Lesson saved = store.replace(lesson);
            if (storedAudio != null) {
                store.deleteAudio(existing.getAudio());
            }
            if (storedCover != null) {
                store.deleteCover(existing.getCover());
            }
            return saved;
        } catch (IOException | RuntimeException ex) {
            if (storedAudio != null) {
                store.deleteAudio(storedAudio.getUrl());
            }
            if (storedCover != null) {
                store.deleteCover(storedCover.getUrl());
            }
            throw ex;
        }
    }

    /**
     * A missing field means the form did not include it, so an update keeps the
     * existing text. A submitted blank value clears the section.
     */
    private String resolveWordsText(String submitted, String existing) {
        if (submitted == null) {
            return existing;
        }
        String value = optional(submitted, MAX_WORDS_TEXT, "Words in this Story");
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void applyMetadata(Lesson lesson, LessonInput input, String title) {
        lesson.setTitle(title);
        lesson.setTitleZh(optional(input.getTitleZh(), 200, "中文标题"));
        lesson.setCategory(defaultIfBlank(optional(input.getCategory(), 60, "分类"), "Listening"));
        lesson.setLevel(defaultIfBlank(optional(input.getLevel(), 40, "难度"), "Intermediate"));
        lesson.setSummary(optional(input.getSummary(), 1000, "简介"));
        lesson.setSummaryZh(optional(input.getSummaryZh(), 1000, "中文简介"));
    }

    private String requireTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty()) {
            throw new AdminValidationException("请填写素材标题。");
        }
        if (title.length() > 200) {
            throw new AdminValidationException("标题请控制在 200 个字符以内。");
        }
        return title;
    }

    private List<String> splitTranscript(String transcriptText) {
        try {
            return transcriptProcessor.split(transcriptText);
        } catch (IllegalArgumentException ex) {
            throw new AdminValidationException(ex.getMessage());
        }
    }

    private String optional(String value, int maxLength, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new AdminValidationException(label + "请控制在 " + maxLength + " 个字符以内。");
        }
        return trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private boolean transcriptChanged(List<TranscriptLine> existing, List<String> sentences) {
        if (existing == null || existing.size() != sentences.size()) {
            return true;
        }
        for (int i = 0; i < sentences.size(); i += 1) {
            TranscriptLine line = existing.get(i);
            if (line == null || line.getText() == null || !line.getText().equals(sentences.get(i))) {
                return true;
            }
        }
        return false;
    }
}
