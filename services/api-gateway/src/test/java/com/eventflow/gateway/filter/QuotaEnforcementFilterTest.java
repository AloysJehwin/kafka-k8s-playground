package com.eventflow.gateway.filter;

import com.eventflow.gateway.billing.BillingQuotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcementFilterTest {

    @Mock
    private BillingQuotaClient billingClient;

    private QuotaEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        filter = new QuotaEnforcementFilter(billingClient);
    }

    // Chain that completes normally — used to verify pass-through behaviour.
    private static final GatewayFilterChain PASS_CHAIN = ex -> Mono.empty();

    @Test
    void nonOrderPath_passthrough() {
        var request = MockServerHttpRequest.get("/api/products/123").build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, PASS_CHAIN))
            .verifyComplete();

        verify(billingClient, never()).checkOrderQuota(any(), any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    void orderPost_quotaNotExceeded_passthrough() {
        var request = MockServerHttpRequest.post("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_ID_ATTR, "tenant-1");
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_PLAN_ATTR, "FREE");

        var quota = new BillingQuotaClient.QuotaStatus("tenant-1", 50L, 100L, false);
        when(billingClient.checkOrderQuota("tenant-1", "FREE")).thenReturn(Mono.just(quota));

        StepVerifier.create(filter.filter(exchange, PASS_CHAIN))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    void orderPost_quotaExceeded_returns402() {
        var request = MockServerHttpRequest.post("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_ID_ATTR, "tenant-2");
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_PLAN_ATTR, "FREE");

        var quota = new BillingQuotaClient.QuotaStatus("tenant-2", 100L, 100L, true);
        when(billingClient.checkOrderQuota("tenant-2", "FREE")).thenReturn(Mono.just(quota));

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain must not be called when quota exceeded"));

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    void orderPost_billingUnreachable_failOpen() {
        var request = MockServerHttpRequest.post("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_ID_ATTR, "tenant-3");
        exchange.getAttributes().put(ApiKeyAuthFilter.TENANT_PLAN_ATTR, "STARTER");

        // Mono.empty() simulates billing-service unreachable (or onErrorResume returning empty)
        when(billingClient.checkOrderQuota("tenant-3", "STARTER")).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, PASS_CHAIN))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    void orderPost_noTenantId_passthrough() {
        var request = MockServerHttpRequest.post("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        // Intentionally no TENANT_ID_ATTR set in exchange attributes

        StepVerifier.create(filter.filter(exchange, PASS_CHAIN))
            .verifyComplete();

        verify(billingClient, never()).checkOrderQuota(any(), any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }
}
