package com.eventflow.webhook;

import com.eventflow.webhook.domain.WebhookEndpoint;
import com.eventflow.webhook.infrastructure.WebhookEndpointRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    properties = {"spring.kafka.bootstrap-servers=localhost:0"},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WebhookControllerTest {

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

    @Autowired MockMvc mvc;
    @Autowired WebhookEndpointRepository endpointRepo;

    @BeforeEach
    void clean() {
        endpointRepo.deleteAll();
    }

    @Test
    void register_returnsCreated() throws Exception {
        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"t1","url":"https://example.com/hook",\
"secret":"super-secret-key","eventTypes":"order.placed,order.completed"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.eventTypes").value("order.placed,order.completed"));
    }

    @Test
    void register_validatesUrl() throws Exception {
        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"t1","url":"not-a-url","secret":"super-secret-key","eventTypes":"order.placed"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void register_validatesSecretLength() throws Exception {
        mvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"t1","url":"https://x.com/h","secret":"short","eventTypes":"order.placed"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsTenantEndpoints() throws Exception {
        endpointRepo.save(new WebhookEndpoint("t2", "https://a.com/h", "secret-key-abcdef", "order.placed"));
        endpointRepo.save(new WebhookEndpoint("t3", "https://b.com/h", "secret-key-abcdef", "order.placed"));

        mvc.perform(get("/api/webhooks").param("tenantId", "t2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].tenantId").value("t2"));
    }

    @Test
    void delete_disablesEndpoint() throws Exception {
        var ep = new WebhookEndpoint("t4", "https://c.com/h", "secret-key-16chars", "order.placed");
        endpointRepo.save(ep);

        mvc.perform(delete("/api/webhooks/" + ep.getId()).param("tenantId", "t4"))
            .andExpect(status().isNoContent());

        var updated = endpointRepo.findById(ep.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus())
            .isEqualTo(com.eventflow.webhook.domain.WebhookStatus.DISABLED);
    }

    @Test
    void delete_wrongTenant_returns404() throws Exception {
        var ep = new WebhookEndpoint("t5", "https://d.com/h", "secret-key-16chars", "order.placed");
        endpointRepo.save(ep);

        mvc.perform(delete("/api/webhooks/" + ep.getId()).param("tenantId", "other-tenant"))
            .andExpect(status().isNotFound());
    }
}
