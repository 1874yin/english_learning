package com.xiaojie.englishlearning.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Translates a batch of sentences with DeepSeek's OpenAI-compatible chat
 * completions API. JSON output mode is used so every sentence comes back with
 * exactly one translation, keeping the transcript timeline aligned.
 */
public class DeepSeekTranslationProvider implements TranslationProvider {

    private static final String SYSTEM_PROMPT =
            "你是一名专业的英译中字幕翻译。用户会给你一个 JSON 数组，每项是一句英文台词。"
            + "请逐句翻译成简体中文，并只返回一个 JSON 对象，格式为："
            + "{\"translations\": [\"第一句的中文\", \"第二句的中文\"]}\n"
            + "要求：\n"
            + "1. translations 的长度必须与输入句子数量完全一致，顺序也必须一致。\n"
            + "2. 必须逐句对应，不能合并、拆分、增加或删除句子。\n"
            + "3. 译文要忠实原意、自然简洁，适合作为学习字幕。\n"
            + "4. 人名、地名、机构名和数字要准确；专有名词可使用通用的中文译名。\n"
            + "5. 使用简体中文和中文标点，不要在译文中换行。\n"
            + "6. 不要输出任何解释、Markdown 或代码块，只输出 JSON。";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;
    private final double temperature;

    public DeepSeekTranslationProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
            String endpoint, String apiKey, String model, int maxOutputTokens,
            double temperature) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    @Override
    public List<String> translate(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }
        if (apiKey.isEmpty()) {
            throw new TranslationUnavailableException("DeepSeek API key is not configured");
        }

        Map<String, Object> body = buildRequestBody(sentences);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            JsonNode response = restTemplate.postForObject(
                    endpoint, new HttpEntity<Map<String, Object>>(body, headers), JsonNode.class);
            return parse(response, sentences.size());
        } catch (HttpStatusCodeException ex) {
            throw new TranslationUnavailableException("DeepSeek returned HTTP "
                    + ex.getRawStatusCode() + ": " + abbreviate(ex.getResponseBodyAsString()), ex);
        } catch (RestClientException ex) {
            throw new TranslationUnavailableException("DeepSeek request failed", ex);
        }
    }

    private Map<String, Object> buildRequestBody(List<String> sentences) {
        String input;
        try {
            input = objectMapper.writeValueAsString(sentences);
        } catch (JsonProcessingException ex) {
            throw new TranslationException("Could not encode transcript for DeepSeek", ex);
        }

        Map<String, Object> systemMessage = new LinkedHashMap<String, Object>();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);

        Map<String, Object> userMessage = new LinkedHashMap<String, Object>();
        userMessage.put("role", "user");
        userMessage.put("content", "共 " + sentences.size()
                + " 句。请翻译下面的 JSON 数组，并返回恰好包含 " + sentences.size()
                + " 个字符串的 translations 数组：\n" + input);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", java.util.Arrays.asList(systemMessage, userMessage));
        body.put("temperature", temperature);
        body.put("max_tokens", maxOutputTokens);
        body.put("response_format", Collections.<String, Object>singletonMap("type", "json_object"));
        body.put("stream", false);
        return body;
    }

    private List<String> parse(JsonNode response, int expectedCount) {
        if (response == null) {
            throw new TranslationUnavailableException("DeepSeek returned an empty response");
        }

        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        String content = contentNode.isMissingNode() ? "" : contentNode.asText("");
        content = stripCodeFence(content).trim();
        if (content.isEmpty()) {
            throw new TranslationException("DeepSeek returned no translation text");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new TranslationAlignmentException("DeepSeek returned invalid JSON");
        }

        JsonNode array = root.isArray() ? root : root.path("translations");
        if (!array.isArray()) {
            throw new TranslationAlignmentException("DeepSeek response has no translations array");
        }
        if (array.size() != expectedCount) {
            throw new TranslationAlignmentException("Expected " + expectedCount
                    + " translations but DeepSeek returned " + array.size());
        }

        List<String> result = new ArrayList<String>(array.size());
        for (JsonNode node : array) {
            String value = node.asText("").trim();
            if (value.isEmpty()) {
                throw new TranslationAlignmentException("DeepSeek returned a blank translation");
            }
            result.add(value);
        }
        return result;
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int closingFence = body.lastIndexOf("```");
        if (closingFence >= 0) {
            body = body.substring(0, closingFence);
        }
        return body.trim();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300) + "...";
    }
}
