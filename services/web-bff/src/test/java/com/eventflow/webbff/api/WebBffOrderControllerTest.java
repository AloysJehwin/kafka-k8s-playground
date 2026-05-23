package com.eventflow.webbff.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "eventflow.schema-registry-url=mock://test"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebBffOrderControllerTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideOrderServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("eventflow.order-service-url",
            () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired
    MockMvc mvc;

    // -----------------------------------------------------------------------
    // test cases
    // -----------------------------------------------------------------------

    @Test
    void placeOrder_unauthenticated_redirectsToLogin() throws Exception {
        // BFF uses server-side OAuth2 flow — unauthenticated requests get a 302 to /oauth2/authorization/google
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderJson()))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void placeOrder_authenticated_delegatesToOrderService() throws Exception {
        // Stub the order-service response
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/api/orders"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", "/api/orders/550e8400-e29b-41d4-a716-446655440000")
                .withBody("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"status\":\"PLACED\"}"))
        );

        mvc.perform(post("/api/orders")
                .with(oauth2Login().attributes(attrs -> attrs.put("sub", "user123")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderJson()))
            .andExpect(status().is2xxSuccessful());

        // Verify the downstream call included the customerId from the OAuth2 sub claim
        wireMock.verify(postRequestedFor(urlEqualTo("/api/orders"))
            .withRequestBody(matchingJsonPath("$.customerId", equalTo("user123"))));
    }

    @Test
    void placeOrder_invalidBody_returns400() throws Exception {
        // productId is missing — validation should reject before hitting order-service
        var invalidBody = """
            {
              "quantity": 1,
              "amount": 9.99,
              "currency": "USD"
            }
            """;

        mvc.perform(post("/api/orders")
                .with(oauth2Login().attributes(attrs -> attrs.put("sub", "user123")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());

        // WireMock should NOT have been called — validation fired first
        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/orders")));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static String validOrderJson() {
        return """
            {
              "productId": "prod-001",
              "quantity": 2,
              "amount": 19.99,
              "currency": "USD"
            }
            """;
    }
}
