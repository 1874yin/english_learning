package com.xiaojie.englishlearning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaojie.englishlearning.testsupport.DatabaseTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * The JSON import preflight runs headless
 * ({@code --spring.main.web-application-type=none}). Web-only beans must not
 * require servlet auto-configuration, otherwise the context cannot start.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.translation.enabled=false",
        "app.user-analysis.api-key=",
        "app.admin.password=test-password",
        "app.storage.root=target/non-web-startup-data"
})
class NonWebApplicationStartupTest extends DatabaseTestContainers {

    @Autowired private ApplicationContext context;

    @Test
    void startsHeadlessWithoutAServletWebServer() {
        assertThat(context).isNotNull();
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }
}
