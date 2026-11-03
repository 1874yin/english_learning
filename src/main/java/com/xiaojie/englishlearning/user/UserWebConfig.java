package com.xiaojie.englishlearning.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UserWebConfig implements WebMvcConfigurer {
    private final ObjectMapper mapper;
    private final ServerProperties serverProperties;

    public UserWebConfig(ObjectMapper mapper, ServerProperties serverProperties) {
        this.mapper = mapper;
        this.serverProperties = serverProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserAuthInterceptor(mapper,
                        serverProperties.getServlet().getSession().getCookie()))
                .addPathPatterns("/api/user/**");
    }

    @Bean
    public java.time.Clock userClock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    public CookieSameSiteSupplier userSessionSameSite() {
        return CookieSameSiteSupplier.ofLax().whenHasName("JSESSIONID");
    }
}
