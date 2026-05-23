package com.eventflow.webbff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {

    private final String defaultTenantId;

    public TenantResolver(@Value("${eventflow.default-tenant-id:system}") String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public String resolve(OAuth2User user) {
        String tenantId = user.getAttribute("tenant_id");
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : defaultTenantId;
    }
}
