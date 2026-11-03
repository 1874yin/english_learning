package com.xiaojie.englishlearning.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DeepSeekTranslationProviderTest {

    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    @Test
    void sendsTheSentencesToDeepSeekAndReturnsTheAlignedTranslations() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model", is("deepseek-chat")))
                .andExpect(jsonPath("$.response_format.type", is("json_object")))
                .andExpect(jsonPath("$.messages[0].role", is("system")))
                .andExpect(jsonPath("$.messages[1].role", is("user")))
                .andExpect(jsonPath("$.messages[1].content", containsString("Hello world.")))
                .andExpect(jsonPath("$.messages[1].content", containsString("This is a test.")))
                .andRespond(withSuccess(chatResponse(
                        "{\"translations\":[\"你好，世界。\",\"这是一个测试。\"]}"),
                        MediaType.APPLICATION_JSON));

        assertThat(provider.translate(Arrays.asList("Hello world.", "This is a test.")))
                .containsExactly("你好，世界。", "这是一个测试。");
        server.verify();
    }

    @Test
    void acceptsAMarkdownFencedJsonResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(chatResponse(
                        "```json\n{\"translations\":[\"你好。\"]}\n```"), MediaType.APPLICATION_JSON));

        assertThat(provider.translate(Arrays.asList("Hello."))).containsExactly("你好。");
    }

    @Test
    void acceptsARawJsonArrayResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(chatResponse("[\"你好。\"]"), MediaType.APPLICATION_JSON));

        assertThat(provider.translate(Arrays.asList("Hello."))).containsExactly("你好。");
    }

    @Test
    void rejectsAResponseWithTheWrongNumberOfTranslations() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(chatResponse(
                        "{\"translations\":[\"只有一句。\"]}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate(Arrays.asList("One.", "Two.")))
                .isInstanceOf(TranslationAlignmentException.class);
    }

    @Test
    void rejectsAResponseThatIsNotJson() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(chatResponse("这不是 JSON"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate(Arrays.asList("Hello.")))
                .isInstanceOf(TranslationAlignmentException.class);
    }

    @Test
    void rejectsAnEmptyCompletion() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "test-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate(Arrays.asList("Hello.")))
                .isInstanceOf(TranslationException.class);
    }

    @Test
    void reportsHttpErrorsAsAnUnavailableService() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DeepSeekTranslationProvider provider = provider(restTemplate, "bad-key");

        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"message\":\"invalid key\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.translate(Arrays.asList("Hello.")))
                .isInstanceOf(TranslationUnavailableException.class)
                .hasMessageContaining("401");
    }

    @Test
    void refusesToCallTheApiWithoutAKey() {
        DeepSeekTranslationProvider provider = provider(new RestTemplate(), "");

        assertThatThrownBy(() -> provider.translate(Arrays.asList("Hello.")))
                .isInstanceOf(TranslationUnavailableException.class)
                .hasMessageContaining("API key");
    }

    private static DeepSeekTranslationProvider provider(RestTemplate restTemplate, String apiKey) {
        return new DeepSeekTranslationProvider(restTemplate, new ObjectMapper(),
                ENDPOINT, apiKey, "deepseek-chat", 8192, 0.2);
    }

    private static String chatResponse(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String escaped = mapper.writeValueAsString(content);
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped + "}}]}";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
