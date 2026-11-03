package com.xiaojie.englishlearning.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates a transcript in batches. Each batch is sent as a list of
 * sentences and must come back as exactly one Chinese sentence per English
 * sentence, in order. If the provider loses the sentence alignment, the batch
 * is retried one sentence at a time.
 */
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final int DEFAULT_MAX_BATCH_CHARS = 6000;
    private static final int MAX_SENTENCES_PER_BATCH = 100;

    private final TranslationProvider provider;
    private final boolean enabled;
    private final int maxBatchChars;

    public TranslationService(TranslationProvider provider, boolean enabled, int maxBatchChars) {
        this.provider = provider;
        this.enabled = enabled;
        this.maxBatchChars = maxBatchChars > 0 ? maxBatchChars : DEFAULT_MAX_BATCH_CHARS;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> translate(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<String>(sentences.size());
        for (List<String> batch : batches(sentences)) {
            checkInterrupted();
            result.addAll(translateBatch(batch));
        }
        return result;
    }

    private List<String> translateBatch(List<String> batch) {
        try {
            List<String> translated = provider.translate(batch);
            return validate(translated, batch.size());
        } catch (TranslationAlignmentException ex) {
            log.debug("Translation alignment failed for {} sentence(s), retrying individually: {}",
                    batch.size(), ex.getMessage());
            return translateIndividually(batch);
        }
    }

    private List<String> translateIndividually(List<String> batch) {
        List<String> result = new ArrayList<String>(batch.size());
        for (String sentence : batch) {
            checkInterrupted();
            List<String> translated = provider.translate(Collections.singletonList(sentence));
            result.add(validate(translated, 1).get(0));
        }
        return result;
    }

    private List<String> validate(List<String> translated, int expectedCount) {
        if (translated == null || translated.size() != expectedCount) {
            throw new TranslationAlignmentException("Expected " + expectedCount
                    + " translation(s) but received "
                    + (translated == null ? "none" : translated.size()));
        }
        List<String> cleaned = new ArrayList<String>(translated.size());
        for (String line : translated) {
            String value = line == null ? "" : line.replaceAll("\\s*\\r?\\n\\s*", " ").trim();
            if (value.isEmpty()) {
                throw new TranslationAlignmentException("Provider returned a blank translation");
            }
            cleaned.add(value);
        }
        return cleaned;
    }

    private List<List<String>> batches(List<String> sentences) {
        List<List<String>> batches = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        int currentChars = 0;
        for (String sentence : sentences) {
            int length = sentence == null ? 0 : sentence.length();
            if (!current.isEmpty() && (currentChars + 1 + length > maxBatchChars
                    || current.size() >= MAX_SENTENCES_PER_BATCH)) {
                batches.add(current);
                current = new ArrayList<String>();
                currentChars = 0;
            }
            current.add(sentence);
            currentChars += current.size() == 1 ? length : 1 + length;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new TranslationInterruptedException("Translation interrupted");
        }
    }
}
