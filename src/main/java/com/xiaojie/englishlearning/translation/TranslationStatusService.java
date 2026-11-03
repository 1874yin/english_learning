package com.xiaojie.englishlearning.translation;

import com.xiaojie.englishlearning.lesson.Lesson;
import com.xiaojie.englishlearning.lesson.LessonStore;
import com.xiaojie.englishlearning.lesson.TranscriptLine;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds a read-only snapshot of the translation setup and how much of the
 * library currently has Chinese subtitles. Used by the admin diagnostics
 * endpoint to confirm the provider switch actually took effect.
 */
@Service
public class TranslationStatusService {

    private final TranslationService translationService;
    private final TranslationState translationState;
    private final LessonStore lessonStore;
    private final String model;

    public TranslationStatusService(TranslationService translationService,
            TranslationState translationState, LessonStore lessonStore,
            @Value("${app.translation.deepseek-model:deepseek-chat}") String model) {
        this.translationService = translationService;
        this.translationState = translationState;
        this.lessonStore = lessonStore;
        this.model = model;
    }

    public TranslationStatus getStatus() {
        int totalLessons = 0;
        int totalLines = 0;
        int translatedLines = 0;
        int translatedLessons = 0;
        int missingLessons = 0;

        for (Lesson lesson : lessonStore.findAll()) {
            totalLessons += 1;
            List<TranscriptLine> lines = lesson.getTranscript();
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            int lessonTranslated = 0;
            for (TranscriptLine line : lines) {
                if (hasTranslation(line)) {
                    lessonTranslated += 1;
                }
            }
            totalLines += lines.size();
            translatedLines += lessonTranslated;
            if (lessonTranslated == lines.size()) {
                translatedLessons += 1;
            } else {
                missingLessons += 1;
            }
        }

        TranslationState.StoredState stored = translationState.readStored();
        return new TranslationStatus(
                translationService.isEnabled(),
                TranslationState.PROVIDER,
                model,
                translationState.isCurrent(),
                stored == null ? null : stored.getProvider(),
                stored == null ? null : stored.getVersion(),
                totalLessons,
                totalLines,
                translatedLines,
                totalLines - translatedLines,
                translatedLessons,
                missingLessons);
    }

    private boolean hasTranslation(TranscriptLine line) {
        return line != null && line.getTranslation() != null
                && !line.getTranslation().trim().isEmpty();
    }
}
