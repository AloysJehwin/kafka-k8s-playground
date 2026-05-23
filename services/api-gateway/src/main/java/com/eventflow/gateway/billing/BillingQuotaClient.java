package com.eventflow.gateway.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class BillingQuotaClient {

    private final WebClient webClient;

    public BillingQuotaClient(@Value("${eventflow.billing-service-url:}") String billingUrl) {
        this.webClient = WebClient.builder().baseUrl(billingUrl).build();
    }

    public record QuotaStatus(String tenantId, long used, long limit, boolean exceeded) {}

    public Mono<QuotaStatus> checkOrderQuota(String tenantId, String plan) {
        return webClient.get()
            .uri(u -> u.path("/api/billing/quota/orders")
                       .queryParam("tenantId", tenantId)
                       .queryParam("plan", plan)
                       .build())
            .retrieve()
            .bodyToMono(QuotaStatus.class)
            .onErrorResume(e -> Mono.empty());
    }
}
