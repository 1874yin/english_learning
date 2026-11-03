package com.xiaojie.englishlearning.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.translation.TranslationService;
import com.xiaojie.englishlearning.translation.TranslationState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LessonServicePaginationTest {

    private final LessonStore store = mock(LessonStore.class);
    private final LessonService service = new LessonService(
            store,
            mock(TranscriptProcessor.class),
            mock(TranslationService.class),
            mock(TranslationState.class));

    @Test
    void returnsRequestedSliceWithTotalAndPageMetadata() {
        when(store.findAll()).thenReturn(lessons(5));

        LessonPage page = service.findPage(1, 2);

        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getItems()).extracting(LessonSummary::getId)
                .containsExactly("id-2", "id-3");
    }

    @Test
    void lastPageStopsPagingAndOutOfRangePageIsEmpty() {
        when(store.findAll()).thenReturn(lessons(5));

        LessonPage last = service.findPage(2, 2);
        assertThat(last.getItems()).extracting(LessonSummary::getId)
                .containsExactly("id-4");
        assertThat(last.isHasMore()).isFalse();

        LessonPage beyond = service.findPage(9, 2);
        assertThat(beyond.getItems()).isEmpty();
        assertThat(beyond.getTotal()).isEqualTo(5);
        assertThat(beyond.isHasMore()).isFalse();
    }

    @Test
    void negativePageAndNonPositiveSizeAreClamped() {
        when(store.findAll()).thenReturn(lessons(3));

        LessonPage page = service.findPage(-4, 0);

        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getItems()).hasSize(1);
    }

    @Test
    void listPayloadCarriesOnlySummaryFields() throws Exception {
        when(store.findAll()).thenReturn(lessons(1));

        JsonNode json = new ObjectMapper()
                .valueToTree(service.findPage(0, 10).getItems().get(0));

        assertThat(json.has("transcript")).isFalse();
        assertThat(json.has("wordsText")).isFalse();
        assertThat(json.has("keyPhrases")).isFalse();
        assertThat(json.has("summary")).isFalse();
        assertThat(json.has("audio")).isFalse();
        assertThat(json.get("title").asText()).isEqualTo("Title 0");
        assertThat(json.get("titleZh").asText()).isEqualTo("标题 0");
        assertThat(json.get("number").asText()).isEqualTo("01");
        assertThat(json.get("cover").asText()).isEqualTo("/images/cover-default.svg");
        assertThat(json.get("duration").asDouble()).isEqualTo(12.5);
    }

    private List<Lesson> lessons(int count) {
        List<Lesson> lessons = new ArrayList<Lesson>();
        for (int i = 0; i < count; i += 1) {
            Lesson lesson = new Lesson();
            lesson.setId("id-" + i);
            lesson.setNumber(String.format("%02d", i + 1));
            lesson.setTitle("Title " + i);
            lesson.setTitleZh("标题 " + i);
            lesson.setCategory("Daily Life");
            lesson.setLevel("Beginner");
            lesson.setDuration(12.5);
            lesson.setCover("/images/cover-default.svg");
            lesson.setAudio("/media/audio-" + i + ".mp3");
            lesson.setSummary("Summary " + i);
            lesson.setWordsText("word");
            TranscriptLine line = new TranscriptLine();
            line.setText("Hello.");
            line.setStart(0);
            line.setEnd(1);
            line.setTranslation("你好。");
            lesson.getTranscript().add(line);
            lessons.add(lesson);
        }
        return lessons;
    }
}
