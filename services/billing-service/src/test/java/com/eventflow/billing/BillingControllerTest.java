package com.eventflow.billing;

import com.eventflow.billing.domain.PlanTier;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.InvoiceRepository;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.YearMonth;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BillingControllerTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired UsageRecordRepository usageRepo;
    @Autowired InvoiceRepository invoiceRepo;

    private final YearMonth thisMonth = YearMonth.now();

    @BeforeEach
    void clean() {
        invoiceRepo.deleteAll();
        usageRepo.deleteAll();
    }

    @Test
    void getUsage_returnsRecordsForTenant() throws Exception {
        usageRepo.save(new UsageRecord("tenant-1", PlanTier.STARTER, thisMonth));

        mvc.perform(get("/api/billing/usage").param("tenantId", "tenant-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].tenantId", is("tenant-1")))
            .andExpect(jsonPath("$[0].plan", is("STARTER")));
    }

    @Test
    void getUsage_emptyForUnknownTenant() throws Exception {
        mvc.perform(get("/api/billing/usage").param("tenantId", "no-such-tenant"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getCurrentUsage_404WhenNoRecord() throws Exception {
        mvc.perform(get("/api/billing/usage/current").param("tenantId", "nobody"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getCurrentUsage_returnsCurrentMonth() throws Exception {
        var rec = new UsageRecord("tenant-2", PlanTier.PRO, thisMonth);
        rec.incrementOrders(5);
        usageRepo.save(rec);

        mvc.perform(get("/api/billing/usage/current").param("tenantId", "tenant-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderCount", is(5)))
            .andExpect(jsonPath("$.plan", is("PRO")));
    }

    @Test
    void orderQuota_notExceededForNewTenant() throws Exception {
        mvc.perform(get("/api/billing/quota/orders")
                .param("tenantId", "new-tenant")
                .param("plan", "FREE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exceeded", is(false)))
            .andExpect(jsonPath("$.limit", is(PlanTier.FREE.orderQuota())));
    }

    @Test
    void getInvoices_emptyForNewTenant() throws Exception {
        mvc.perform(get("/api/billing/invoices").param("tenantId", "tenant-new"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
