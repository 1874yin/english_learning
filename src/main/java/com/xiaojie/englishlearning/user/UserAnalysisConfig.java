package com.xiaojie.englishlearning.user;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.client.RestTemplate;

@Configuration
@PropertySource("classpath:user.properties")
public class UserAnalysisConfig {
    @Bean
    public RestTemplate userAnalysisRestTemplate(RestTemplateBuilder builder,
            @Value("${app.user-analysis.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${app.user-analysis.read-timeout-ms:60000}") int readTimeout) {
        return builder.setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout)).build();
    }
}
