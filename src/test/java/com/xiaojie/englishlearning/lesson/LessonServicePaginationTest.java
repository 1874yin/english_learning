package com.xiaojie.englishlearning.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaojie.englishlearning.translation.TranslationService;
import com.xiaojie.englishlearning.translation.TranslationState;
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
    void returnsRequestedDatabasePageAndMetadata() {
        LessonPage expected = new LessonPage(List.of(summary("id-2"), summary("id-3")), 1, 2, 5);
        when(store.findPage(1, 2)).thenReturn(expected);

        LessonPage actual = service.findPage(1, 2);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getPage()).isEqualTo(1);
        assertThat(actual.getSize()).isEqualTo(2);
        assertThat(actual.getTotal()).isEqualTo(5);
        assertThat(actual.isHasMore()).isTrue();
        assertThat(actual.getItems()).extracting(LessonSummary::getId)
                .containsExactly("id-2", "id-3");
        verify(store).findPage(1, 2);
    }

    @Test
    void databasePageHandlesLastAndOutOfRangePages() {
        LessonPage last = new LessonPage(List.of(summary("id-4")), 2, 2, 5);
        LessonPage beyond = new LessonPage(List.of(), 9, 2, 5);
        when(store.findPage(2, 2)).thenReturn(last);
        when(store.findPage(9, 2)).thenReturn(beyond);

        assertThat(service.findPage(2, 2).getItems()).extracting(LessonSummary::getId)
                .containsExactly("id-4");
        assertThat(service.findPage(2, 2).isHasMore()).isFalse();
        assertThat(service.findPage(9, 2).getItems()).isEmpty();
        assertThat(service.findPage(9, 2).getTotal()).isEqualTo(5);
        assertThat(service.findPage(9, 2).isHasMore()).isFalse();
    }

    @Test
    void clampsPageAndSizeBeforeQueryingDatabase() {
        LessonPage expected = new LessonPage(List.of(summary("id-0")), 0, 1, 3);
        when(store.findPage(0, 1)).thenReturn(expected);

        LessonPage actual = service.findPage(-4, 0);

        assertThat(actual).isSameAs(expected);
        verify(store).findPage(0, 1);
    }

    @Test
    void lessonSummaryJsonContainsOnlyPlaylistFields() throws Exception {
        JsonNode json = new ObjectMapper().valueToTree(summary("id-0"));

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

    private LessonSummary summary(String id) {
        return new LessonSummary(id, "01", "Title 0", "标题 0", "Daily Life", "Beginner", 12.5,
                "/images/cover-default.svg");
    }
}
