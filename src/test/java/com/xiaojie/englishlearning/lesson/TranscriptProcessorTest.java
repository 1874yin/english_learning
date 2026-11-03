package com.xiaojie.englishlearning.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptProcessorTest {

    private final TranscriptProcessor processor = new TranscriptProcessor();

    @Test
    void splitsSentencesWithoutBreakingAbbreviationsDecimalsOrDialogueTags() {
        List<String> sentences = processor.split(
                "Dr. Smith paid 3.50 dollars. \"Are you ready?\" she asked. Yes!\n\n"
                        + "This paragraph has two sentences. The second one ends here.");

        assertThat(sentences).containsExactly(
                "Dr. Smith paid 3.50 dollars.",
                "\"Are you ready?\" she asked.",
                "Yes!",
                "This paragraph has two sentences.",
                "The second one ends here.");
    }

    @Test
    void splitsUnpunctuatedLines() {
        List<String> sentences = processor.split("First line\nSecond line\n\nThird line");

        assertThat(sentences).containsExactly("First line", "Second line", "Third line");
    }

    @Test
    void estimatesMonotonicTimelineAcrossFullAudioDuration() {
        List<TranscriptLine> lines = processor.estimateTimeline(
                java.util.Arrays.asList("A short line.", "This sentence is noticeably longer than the first one.",
                        "One more sentence."),
                30.0);

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).getStart()).isEqualTo(0.0);
        assertThat(lines.get(lines.size() - 1).getEnd()).isEqualTo(30.0);
        for (int i = 0; i < lines.size(); i += 1) {
            assertThat(lines.get(i).getEnd()).isGreaterThan(lines.get(i).getStart());
            if (i > 0) {
                assertThat(lines.get(i).getStart()).isGreaterThanOrEqualTo(lines.get(i - 1).getEnd());
            }
        }
    }

    @Test
    void rejectsEmptyTranscript() {
        assertThatThrownBy(() -> processor.split("   \n\n  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
