package com.xiaojie.englishlearning.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TranslationConfig {

    private static final Logger log = LoggerFactory.getLogger(TranslationConfig.class);

    @Bean
    public RestTemplate translationRestTemplate(RestTemplateBuilder builder,
            @Value("${app.translation.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${app.translation.read-timeout-ms:120000}") int readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Bean
    public TranslationProvider deepSeekTranslationProvider(
            RestTemplate translationRestTemplate,
            ObjectMapper objectMapper,
            @Value("${app.translation.deepseek-url:https://api.deepseek.com/chat/completions}")
                    String endpoint,
            @Value("${app.translation.deepseek-api-key:}") String apiKey,
            @Value("${app.translation.deepseek-model:deepseek-chat}") String model,
            @Value("${app.translation.deepseek-max-output-tokens:8192}") int maxOutputTokens,
            @Value("${app.translation.deepseek-temperature:0.2}") double temperature) {
        return new DeepSeekTranslationProvider(translationRestTemplate, objectMapper,
                endpoint, apiKey, model, maxOutputTokens, temperature);
    }

    @Bean
    public TranslationService translationService(
            TranslationProvider deepSeekTranslationProvider,
            @Value("${app.translation.enabled:true}") boolean enabled,
            @Value("${app.translation.deepseek-api-key:}") String apiKey,
            @Value("${app.translation.deepseek-model:deepseek-chat}") String model,
            @Value("${app.translation.max-batch-chars:6000}") int maxBatchChars) {
        boolean configured = enabled && StringUtils.hasText(apiKey);
        if (configured) {
            log.info("Automatic Chinese translation is enabled with DeepSeek "
                    + "(model={}, batch limit={} chars)", model, maxBatchChars);
        } else if (enabled) {
            log.warn("Automatic translation is enabled but TRANSLATION_DEEPSEEK_API_KEY is empty; "
                    + "Chinese subtitles will not be generated.");
        }
        return new TranslationService(deepSeekTranslationProvider, configured, maxBatchChars);
    }
}
