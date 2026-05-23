package com.eventflow.order.config;

import com.eventflow.order.tenant.TenantContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantMvcConfig implements WebMvcConfigurer {

    private final TenantContextInterceptor interceptor;

    public TenantMvcConfig(TenantContextInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
