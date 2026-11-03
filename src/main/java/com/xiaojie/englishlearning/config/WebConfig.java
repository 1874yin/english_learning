package com.xiaojie.englishlearning.config;

import com.xiaojie.englishlearning.admin.AdminAuthInterceptor;
import com.xiaojie.englishlearning.storage.StoragePaths;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StoragePaths storagePaths;
    private final boolean allowLocalAdminRequests;

    public WebConfig(StoragePaths storagePaths,
            @Value("${app.admin.allow-local-requests:true}") boolean allowLocalAdminRequests) {
        this.storagePaths = storagePaths;
        this.allowLocalAdminRequests = allowLocalAdminRequests;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(storagePaths.getAudioLocation())
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(allowLocalAdminRequests))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login", "/api/admin/logout");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin.html");
        registry.addRedirectViewController("/admin/", "/admin.html");
    }
}
