package com.eventflow.tenant.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);

    private final RestClient restClient;

    public BillingClient(@Value("${eventflow.billing-service-url:}") String billingUrl) {
        this.restClient = RestClient.builder().baseUrl(billingUrl).build();
    }

    public record PlanSyncRequest(String tenantId, String plan) {}

    public void syncPlan(String tenantId, String plan) {
        try {
            restClient.put()
                .uri("/api/admin/billing/usage/plan")
                .body(new PlanSyncRequest(tenantId, plan))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            // Non-fatal: billing-service may be temporarily unavailable.
            // The next order event will re-create the record with the correct plan.
            log.warn("Failed to sync plan to billing-service tenantId={} plan={}: {}", tenantId, plan, e.getMessage());
        }
    }
}
