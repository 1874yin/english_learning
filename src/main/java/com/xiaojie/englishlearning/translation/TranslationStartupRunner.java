package com.xiaojie.englishlearning.translation;

import com.xiaojie.englishlearning.lesson.LessonService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TranslationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TranslationStartupRunner.class);

    private final LessonService lessonService;
    private final TranslationService translationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lesson-translation");
        thread.setDaemon(true);
        return thread;
    });

    public TranslationStartupRunner(LessonService lessonService, TranslationService translationService) {
        this.lessonService = lessonService;
        this.translationService = translationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!translationService.isEnabled()) {
            return;
        }
        executor.submit(() -> {
            try {
                int translated = lessonService.translateMissingLessons();
                if (translated > 0) {
                    log.info("Added Chinese subtitles to {} lesson(s)", translated);
                }
            } catch (RuntimeException ex) {
                log.warn("Startup Chinese translation failed", ex);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
