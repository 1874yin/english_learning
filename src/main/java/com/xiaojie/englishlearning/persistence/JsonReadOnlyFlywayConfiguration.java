package com.xiaojie.englishlearning.persistence;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.persistence", name = "mode", havingValue = "json-read-only")
class JsonReadOnlyFlywayConfiguration {

    @Bean
    FlywayMigrationStrategy skipFlywayForJsonReadOnlyMode() {
        return flyway -> { };
    }
}
