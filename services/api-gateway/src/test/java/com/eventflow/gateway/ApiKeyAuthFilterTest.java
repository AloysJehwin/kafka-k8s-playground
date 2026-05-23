package com.eventflow.gateway;

import com.eventflow.gateway.filter.ApiKeyAuthFilter;
import com.eventflow.gateway.tenant.TenantLookupClient;
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

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private TenantLookupClient tenantLookup;

    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(tenantLookup, "internal-secret");
    }

    @Test
    void missingApiKey_returns401() {
        var request = MockServerHttpRequest.get("/api/orders/1").build();
        var exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not be called"));

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidApiKey_returns401() {
        when(tenantLookup.resolve("bad-key")).thenReturn(Mono.empty());

        var request = MockServerHttpRequest.get("/api/orders/1")
            .header("X-Api-Key", "bad-key")
            .build();
        var exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not be called"));

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suspendedTenant_returns401() {
        var suspended = new TenantLookupClient.TenantInfo("tid-1", "acme", "FREE", "SUSPENDED");
        when(tenantLookup.resolve("suspended-key")).thenReturn(Mono.just(suspended));

        var request = MockServerHttpRequest.get("/api/orders/1")
            .header("X-Api-Key", "suspended-key")
            .build();
        var exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not be called"));

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validApiKey_injectsTenantId() {
        var active = new TenantLookupClient.TenantInfo("tid-42", "acme", "FREE", "ACTIVE");
        when(tenantLookup.resolve("valid-key")).thenReturn(Mono.just(active));

        var request = MockServerHttpRequest.get("/api/orders/1")
            .header("X-Api-Key", "valid-key")
            .build();
        var exchange = MockServerWebExchange.from(request);

        // Capture the mutated exchange the chain receives
        AtomicReference<String> capturedTenantId = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedTenantId.set(ex.getRequest().getHeaders().getFirst("X-Tenant-Id"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(capturedTenantId.get()).isEqualTo("tid-42");
    }
}
