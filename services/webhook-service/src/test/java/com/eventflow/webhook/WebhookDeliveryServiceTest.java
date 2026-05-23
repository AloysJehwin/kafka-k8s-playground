package com.eventflow.webhook;

import com.eventflow.webhook.delivery.WebhookDeliveryService;
import com.eventflow.webhook.domain.DeliveryStatus;
import com.eventflow.webhook.domain.WebhookDelivery;
import com.eventflow.webhook.domain.WebhookEndpoint;
import com.eventflow.webhook.infrastructure.WebhookDeliveryRepository;
import com.eventflow.webhook.infrastructure.WebhookEndpointRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {"spring.kafka.bootstrap-servers=localhost:0"},
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WebhookDeliveryServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("webhooks")
            .withUsername("webhook")
            .withPassword("webhook");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WebhookDeliveryService deliveryService;
    @Autowired WebhookEndpointRepository endpointRepo;
    @Autowired WebhookDeliveryRepository deliveryRepo;

    WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
        deliveryRepo.deleteAll();
        endpointRepo.deleteAll();
    }

    @Test
    void successfulDelivery_marksDelivered() {
        wireMock.stubFor(post(urlEqualTo("/hook"))
            .willReturn(aResponse().withStatus(200)));

        var ep = new WebhookEndpoint("tenant-1",
            "http://localhost:" + wireMock.port() + "/hook",
            "test-secret", "order.placed");
        endpointRepo.save(ep);

        deliveryService.dispatch("tenant-1", "order.placed", "order-1",
            "{\"event\":\"order.placed\",\"orderId\":\"order-1\"}");

        List<WebhookDelivery> deliveries = deliveryRepo.findByEndpointId(ep.getId());
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(deliveries.get(0).getLastHttpStatus()).isEqualTo(200);

        wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
            .withHeader("X-EventFlow-Event", equalTo("order.placed"))
            .withHeader("X-EventFlow-Signature", matching("sha256=[a-f0-9]{64}")));
    }

    @Test
    void http500_marksFailedAndSchedulesRetry() {
        wireMock.stubFor(post(urlEqualTo("/hook"))
            .willReturn(aResponse().withStatus(500)));

        var ep = new WebhookEndpoint("tenant-1",
            "http://localhost:" + wireMock.port() + "/hook",
            "secret", "order.placed");
        endpointRepo.save(ep);

        deliveryService.dispatch("tenant-1", "order.placed", "order-2", "{\"event\":\"order.placed\"}");

        List<WebhookDelivery> deliveries = deliveryRepo.findByEndpointId(ep.getId());
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(deliveries.get(0).getNextAttemptAt()).isNotNull();
    }

    @Test
    void unsubscribedEventType_skipped() {
        var ep = new WebhookEndpoint("tenant-1",
            "http://localhost:" + wireMock.port() + "/hook",
            "secret", "order.completed");
        endpointRepo.save(ep);

        deliveryService.dispatch("tenant-1", "order.placed", "order-3", "{\"event\":\"order.placed\"}");

        assertThat(deliveryRepo.findByEndpointId(ep.getId())).isEmpty();
        wireMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void disabledEndpoint_skipped() {
        var ep = new WebhookEndpoint("tenant-1",
            "http://localhost:" + wireMock.port() + "/hook",
            "secret", "order.placed");
        ep.disable();
        endpointRepo.save(ep);

        deliveryService.dispatch("tenant-1", "order.placed", "order-4", "{\"event\":\"order.placed\"}");

        assertThat(deliveryRepo.findByEndpointId(ep.getId())).isEmpty();
    }

    @Test
    void signatureHeader_matchesHmac() {
        wireMock.stubFor(post(urlEqualTo("/hook"))
            .willReturn(aResponse().withStatus(200)));

        String secret = "my-secret";
        String payload = "{\"event\":\"order.placed\"}";
        var ep = new WebhookEndpoint("tenant-2",
            "http://localhost:" + wireMock.port() + "/hook", secret, "order.placed");
        endpointRepo.save(ep);

        deliveryService.dispatch("tenant-2", "order.placed", "order-5", payload);

        String expectedSig = "sha256=" + WebhookDeliveryService.hmacSha256(secret, payload);
        wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
            .withHeader("X-EventFlow-Signature", equalTo(expectedSig)));
    }
}
