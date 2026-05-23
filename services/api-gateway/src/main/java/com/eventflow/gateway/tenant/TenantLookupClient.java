package com.eventflow.gateway.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TenantLookupClient {

    private final WebClient webClient;

    public TenantLookupClient(@Value("${eventflow.tenant-service-url}") String tenantServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(tenantServiceUrl).build();
    }

    public record TenantInfo(String id, String slug, String plan, String status) {}

    /**
     * Resolves an API key to a TenantInfo by calling GET /api/tenants/by-api-key/{apiKey}.
     * Returns empty Mono when the key is not found or the call fails.
     */
    public Mono<TenantInfo> resolve(String apiKey) {
        return webClient.get()
            .uri("/api/tenants/by-api-key/{key}", apiKey)
            .retrieve()
            .bodyToMono(TenantInfo.class)
            .onErrorResume(e -> Mono.empty());
    }
}
