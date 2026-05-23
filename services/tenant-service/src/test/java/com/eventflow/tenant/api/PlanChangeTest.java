package com.eventflow.tenant.api;

import com.eventflow.tenant.billing.BillingClient;
import com.eventflow.tenant.domain.Tenant;
import com.eventflow.tenant.domain.TenantPlan;
import com.eventflow.tenant.infrastructure.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@TestPropertySource(properties = "eventflow.internal-token=test-token")
class PlanChangeTest {

    private static final String INTERNAL_TOKEN = "test-token";
    private static final String PLAN_URL = "/api/tenants/{id}/plan";
    private static final String ADMIN_ALL_URL = "/api/tenants/admin/all";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TenantRepository repository;

    @MockBean
    BillingClient billingClient;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant(tenantId, "acme-corp", "Acme Corp", TenantPlan.FREE);
        doNothing().when(billingClient).syncPlan(any(), any());
    }

    @Test
    void patchPlan_success() throws Exception {
        when(repository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(repository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch(PLAN_URL, tenantId)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"STARTER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan", equalTo("STARTER")))
            .andExpect(jsonPath("$.id", equalTo(tenantId.toString())));

        verify(billingClient).syncPlan(tenantId.toString(), "STARTER");
    }

    @Test
    void patchPlan_forbiddenWithoutToken() throws Exception {
        mockMvc.perform(patch(PLAN_URL, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"STARTER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void patchPlan_forbiddenWithWrongToken() throws Exception {
        mockMvc.perform(patch(PLAN_URL, tenantId)
                .header("X-Internal-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"STARTER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void patchPlan_notFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(patch(PLAN_URL, unknownId)
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"STARTER\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listAll_success() throws Exception {
        Tenant second = new Tenant(UUID.randomUUID(), "beta-org", "Beta Org", TenantPlan.PRO);
        when(repository.findAll()).thenReturn(List.of(tenant, second));

        mockMvc.perform(get(ADMIN_ALL_URL)
                .header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listAll_forbidden() throws Exception {
        mockMvc.perform(get(ADMIN_ALL_URL))
            .andExpect(status().isForbidden());
    }
}
