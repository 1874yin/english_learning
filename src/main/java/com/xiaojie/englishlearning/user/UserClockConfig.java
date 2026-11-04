package com.xiaojie.englishlearning.user;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock used by user services. Kept out of the web configuration so non-web
 * runs (for example {@code --spring.main.web-application-type=none} data
 * imports) still have it.
 */
@Configuration
public class UserClockConfig {

    @Bean
    public Clock userClock() {
        return Clock.systemUTC();
    }
}
