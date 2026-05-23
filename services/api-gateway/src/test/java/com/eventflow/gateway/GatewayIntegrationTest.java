package com.eventflow.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegrationTest {

    // -----------------------------------------------------------------------
    // WireMock servers — tenant-service and order-service stubs
    // -----------------------------------------------------------------------

    static WireMockServer tenantWireMock;
    static WireMockServer orderWireMock;

    @BeforeAll
    static void startWireMock() {
        tenantWireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        orderWireMock  = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        tenantWireMock.start();
        orderWireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        tenantWireMock.stop();
        orderWireMock.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("eventflow.tenant-service-url", () -> "http://localhost:" + tenantWireMock.port());
        registry.add("ORDER_SERVICE_URL",             () -> "http://localhost:" + orderWireMock.port());
        registry.add("TENANT_SERVICE_URL",            () -> "http://localhost:" + tenantWireMock.port());
    }

    // -----------------------------------------------------------------------
    // Stub setup
    // -----------------------------------------------------------------------

    @BeforeEach
    void stubTenantService() {
        tenantWireMock.resetAll();
        orderWireMock.resetAll();

        // valid-key → ACTIVE tenant (used by rate-limit test — tenant-1, FREE plan, 100 req/min)
        tenantWireMock.stubFor(
            get(urlEqualTo("/api/tenants/by-api-key/valid-key"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"tenant-1\",\"slug\":\"acme\",\"plan\":\"FREE\",\"status\":\"ACTIVE\"}")));

        // routing-key → ACTIVE tenant (dedicated key for the routing test — separate rate-limit bucket)
        tenantWireMock.stubFor(
            get(urlEqualTo("/api/tenants/by-api-key/routing-key"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"tenant-3\",\"slug\":\"routing\",\"plan\":\"FREE\",\"status\":\"ACTIVE\"}")));

        // suspended-key → SUSPENDED tenant
        tenantWireMock.stubFor(
            get(urlEqualTo("/api/tenants/by-api-key/suspended-key"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"tenant-2\",\"slug\":\"suspended\",\"plan\":\"FREE\",\"status\":\"SUSPENDED\"}")));

        // unknown-key → 404 (catch-all, lowest priority)
        tenantWireMock.stubFor(
            get(urlMatching("/api/tenants/by-api-key/.*"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(404)));

        // order-service returns empty list
        orderWireMock.stubFor(
            get(urlMatching("/api/orders.*"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));
    }

    // -----------------------------------------------------------------------
    // WebTestClient — injected after gateway starts
    // -----------------------------------------------------------------------

    @Autowired
    WebTestClient webTestClient;

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void missingApiKey_returns401() {
        webTestClient.get()
            .uri("/api/orders?customerId=x")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void invalidApiKey_returns401() {
        webTestClient.get()
            .uri("/api/orders?customerId=x")
            .header("X-Api-Key", "unknown-key")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void suspendedTenant_returns401() {
        webTestClient.get()
            .uri("/api/orders?customerId=x")
            .header("X-Api-Key", "suspended-key")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void validApiKey_routesToOrderService() {
        // Uses routing-key (tenant-3) to avoid sharing the rate-limit counter with rateLimitExceeded test
        webTestClient.get()
            .uri("/api/orders?customerId=x")
            .header("X-Api-Key", "routing-key")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");

        // Verify the downstream call carried the injected headers
        orderWireMock.verify(
            getRequestedFor(urlMatching("/api/orders.*"))
                .withHeader("X-Tenant-Id", equalTo("tenant-3"))
                .withHeader("X-Internal-Token", equalTo("test-token")));
    }

    @Test
    void rateLimitExceeded_returns429() {
        // FREE plan allows 100 req/min — the 101st must be rejected
        List<Integer> statuses = new ArrayList<>(101);

        for (int i = 0; i < 101; i++) {
            int status = webTestClient.get()
                .uri("/api/orders?customerId=x")
                .header("X-Api-Key", "valid-key")
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
            statuses.add(status);
        }

        assertThat(statuses).anyMatch(s -> s == 429);
    }
}
