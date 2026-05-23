package com.eventflow.order;

import com.eventflow.order.api.OrderController.OrderResponse;
import com.eventflow.order.api.OrderController.PlaceOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class OrderControllerIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders")
            .withUsername("eventflow")
            .withPassword("eventflow");

    @Container
    static final KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eventflow.schema-registry-url", () -> "mock://test");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String base() {
        return "http://localhost:" + port + "/api/orders";
    }

    private PlaceOrderRequest validRequest(String customerId) {
        return new PlaceOrderRequest(customerId, "prod-001", 2,
            new BigDecimal("19.99"), "USD");
    }

    // -----------------------------------------------------------------------
    // test cases
    // -----------------------------------------------------------------------

    @Test
    void placeOrder_returns201AndCreatesOrder() {
        var response = rest.postForEntity(base(), validRequest("cust-A"), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("PLACED");
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void placeOrder_invalidPayload_returns400() {
        // productId is @NotBlank — send blank string to trigger validation
        var invalid = new PlaceOrderRequest("cust-A", "", 2,
            new BigDecimal("9.99"), "USD");

        var response = rest.postForEntity(base(), invalid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getOrder_notFound_returns404() {
        var randomId = UUID.randomUUID();
        var response = rest.getForEntity(base() + "/" + randomId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listOrders_returnsOnlyCustomerOrders() {
        // Place 2 orders for customerA, 1 for customerB
        rest.postForEntity(base(), validRequest("customerA"), OrderResponse.class);
        rest.postForEntity(base(), validRequest("customerA"), OrderResponse.class);
        rest.postForEntity(base(), validRequest("customerB"), OrderResponse.class);

        var response = rest.getForEntity(
            base() + "?customerId=customerA",
            List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // customerA should have at least 2 (tests run in shared context, could be more)
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);

        // Verify none of the returned orders belong to customerB
        @SuppressWarnings("unchecked")
        var orders = (List<Map<String, Object>>) response.getBody();
        assertThat(orders).allSatisfy(o ->
            assertThat(o.get("customerId")).isEqualTo("customerA")
        );
    }

    @Test
    void getOrder_wrongCustomer_returns403() {
        // Place order as customerA
        var placed = rest.postForEntity(base(), validRequest("customerA"), OrderResponse.class);
        assertThat(placed.getBody()).isNotNull();
        var orderId = placed.getBody().id();

        // Try to retrieve as customerB
        var headers = new HttpHeaders();
        headers.set("X-Customer-Id", "customerB");
        var request = new HttpEntity<>(headers);

        var response = rest.exchange(
            base() + "/" + orderId,
            HttpMethod.GET,
            request,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
