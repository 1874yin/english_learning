package com.xiaojie.englishlearning.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TranslationServiceTest {

    @Test
    void translatesTheWholeBatchWhenItFitsTheLimit() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 1000);

        List<String> result = service.translate(Arrays.asList("Hello world.", "This is a test."));

        assertThat(result).containsExactly("译：Hello world.", "译：This is a test.");
        assertThat(provider.calls).hasSize(1);
        assertThat(provider.calls.get(0)).containsExactly("Hello world.", "This is a test.");
    }

    @Test
    void splitsIntoBatchesAtTheConfiguredCharacterLimit() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 30);

        List<String> result = service.translate(Arrays.asList(
                "First sentence is here.",
                "Second sentence is here.",
                "Third sentence is here."));

        assertThat(result).containsExactly(
                "译：First sentence is here.",
                "译：Second sentence is here.",
                "译：Third sentence is here.");
        assertThat(provider.calls).hasSize(3);
        assertThat(provider.calls).allMatch(batch -> charCount(batch) <= 30);
    }

    @Test
    void splitsBatchesAtTheSentenceCountLimit() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 100000);

        List<String> sentences = new ArrayList<String>();
        for (int i = 0; i < 101; i += 1) {
            sentences.add("S" + i + ".");
        }

        List<String> result = service.translate(sentences);

        assertThat(result).hasSize(101);
        assertThat(provider.calls).hasSize(2);
        assertThat(provider.calls.get(0)).hasSize(100);
        assertThat(provider.calls.get(1)).hasSize(1);
    }

    @Test
    void keepsSentenceOrderAcrossBatches() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 4);

        List<String> result = service.translate(Arrays.asList("One.", "Two.", "Three."));

        assertThat(result).containsExactly("译：One.", "译：Two.", "译：Three.");
        assertThat(provider.calls).hasSize(3);
    }

    @Test
    void retriesIndividuallyWhenTheBatchReturnsTheWrongNumberOfTranslations() {
        StubProvider provider = new StubProvider(batch -> batch.size() > 1
                ? Collections.singletonList("只有一句")
                : Collections.singletonList("单句：" + batch.get(0)));
        TranslationService service = new TranslationService(provider, true, 1000);

        List<String> result = service.translate(Arrays.asList("One.", "Two."));

        assertThat(result).containsExactly("单句：One.", "单句：Two.");
        assertThat(provider.calls).hasSize(3);
    }

    @Test
    void retriesIndividuallyWhenTheProviderReportsAnAlignmentProblem() {
        StubProvider provider = new StubProvider(batch -> {
            if (batch.size() > 1) {
                throw new TranslationAlignmentException("wrong count");
            }
            return Collections.singletonList("单句：" + batch.get(0));
        });
        TranslationService service = new TranslationService(provider, true, 1000);

        List<String> result = service.translate(Arrays.asList("One.", "Two."));

        assertThat(result).containsExactly("单句：One.", "单句：Two.");
        assertThat(provider.calls).hasSize(3);
    }

    @Test
    void propagatesAProviderFailureWithoutRetryingEverySentence() {
        StubProvider provider = new StubProvider(batch -> {
            throw new TranslationException("DeepSeek is down");
        });
        TranslationService service = new TranslationService(provider, true, 1000);

        assertThatThrownBy(() -> service.translate(Arrays.asList("One.", "Two.")))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("DeepSeek is down");
        assertThat(provider.calls).hasSize(1);
    }

    @Test
    void propagatesFailureOfAnIndividualRetry() {
        StubProvider provider = new StubProvider(batch -> {
            if (batch.size() > 1) {
                return Collections.singletonList("只有一句");
            }
            throw new TranslationException("single request failed");
        });
        TranslationService service = new TranslationService(provider, true, 1000);

        assertThatThrownBy(() -> service.translate(Arrays.asList("One.", "Two.")))
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("single request failed");
    }

    @Test
    void rejectsBlankTranslationsFromTheProvider() {
        StubProvider provider = new StubProvider(batch -> Arrays.asList("译文1。", "  "));
        TranslationService service = new TranslationService(provider, true, 1000);

        assertThatThrownBy(() -> service.translate(Arrays.asList("One.", "Two.")))
                .isInstanceOf(TranslationException.class);
    }

    @Test
    void returnsAnEmptyListForEmptyInput() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 1000);

        assertThat(service.translate(Collections.<String>emptyList())).isEmpty();
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void stopsWhenTheThreadIsInterrupted() {
        StubProvider provider = new StubProvider(batch -> translateAll(batch));
        TranslationService service = new TranslationService(provider, true, 1000);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.translate(Arrays.asList("One.", "Two.")))
                    .isInstanceOf(TranslationInterruptedException.class);
        } finally {
            Thread.interrupted();
        }
        assertThat(provider.calls).isEmpty();
    }

    private static List<String> translateAll(List<String> batch) {
        List<String> result = new ArrayList<String>(batch.size());
        for (String sentence : batch) {
            result.add("译：" + sentence);
        }
        return result;
    }

    private static int charCount(List<String> batch) {
        int total = 0;
        for (String sentence : batch) {
            total += sentence.length() + (total == 0 ? 0 : 1);
        }
        return total;
    }

    private static final class StubProvider implements TranslationProvider {

        private final Function<List<String>, List<String>> response;
        private final List<List<String>> calls = new ArrayList<List<String>>();

        private StubProvider(Function<List<String>, List<String>> response) {
            this.response = response;
        }

        @Override
        public List<String> translate(List<String> sentences) {
            calls.add(new ArrayList<String>(sentences));
            return response.apply(sentences);
        }
    }
}
