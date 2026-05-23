package com.eventflow.order.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = TenantContext.get();
        if (tenantId == null && !request.getRequestURI().startsWith("/actuator")) {
            log.warn("Request to {} has no tenant context — X-Tenant-Id header missing", request.getRequestURI());
        }
        return true;
    }
}
