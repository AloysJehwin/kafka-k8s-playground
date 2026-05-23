package com.eventflow.billing.api;

import com.eventflow.billing.domain.Invoice;
import com.eventflow.billing.domain.InvoiceStatus;
import com.eventflow.billing.domain.PlanTier;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.InvoiceRepository;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import com.eventflow.billing.metering.UsageAccumulator;
import com.eventflow.billing.metering.UsageAccumulator.QuotaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final UsageRecordRepository usageRepo;
    private final InvoiceRepository invoiceRepo;
    private final UsageAccumulator accumulator;
    private final String internalToken;

    public BillingController(UsageRecordRepository usageRepo,
                              InvoiceRepository invoiceRepo,
                              UsageAccumulator accumulator,
                              @Value("${eventflow.internal-token:}") String internalToken) {
        this.usageRepo = usageRepo;
        this.invoiceRepo = invoiceRepo;
        this.accumulator = accumulator;
        this.internalToken = internalToken;
    }

    record UsageResponse(
        UUID id, String tenantId, String plan,
        int billingYear, int billingMonth,
        long orderCount, long webhookDeliveryCount, long apiCallCount,
        long orderQuota, boolean orderQuotaExceeded,
        Instant updatedAt
    ) {
        static UsageResponse from(UsageRecord r) {
            return new UsageResponse(
                r.getId(), r.getTenantId(), r.getPlan().name(),
                r.getBillingYear(), r.getBillingMonth(),
                r.getOrderCount(), r.getWebhookDeliveryCount(), r.getApiCallCount(),
                r.getPlan().orderQuota(), r.isOrderQuotaExceeded(),
                r.getUpdatedAt()
            );
        }
    }

    record InvoiceResponse(
        UUID id, String tenantId, String plan,
        int billingYear, int billingMonth,
        long orderCount, long webhookDeliveryCount, long apiCallCount,
        BigDecimal amountUsd, String status, Instant createdAt
    ) {
        static InvoiceResponse from(Invoice inv) {
            return new InvoiceResponse(
                inv.getId(), inv.getTenantId(), inv.getPlan().name(),
                inv.getBillingYear(), inv.getBillingMonth(),
                inv.getOrderCount(), inv.getWebhookDeliveryCount(), inv.getApiCallCount(),
                inv.getAmountUsd(), inv.getStatus().name(), inv.getCreatedAt()
            );
        }
    }

    record QuotaResponse(String tenantId, long used, long limit, boolean exceeded) {}

    record PlanSyncRequest(@NotBlank String tenantId, @NotNull String plan) {}

    // -----------------------------------------------------------------------
    // Tenant-facing read endpoints
    // -----------------------------------------------------------------------

    @GetMapping("/usage")
    public List<UsageResponse> usage(@RequestParam String tenantId) {
        return usageRepo.findByTenantIdOrderByBillingYearDescBillingMonthDesc(tenantId)
            .stream().map(UsageResponse::from).toList();
    }

    @GetMapping("/usage/current")
    public ResponseEntity<UsageResponse> currentUsage(@RequestParam String tenantId) {
        YearMonth now = YearMonth.now();
        return usageRepo.findByTenantIdAndBillingYearAndBillingMonth(
                tenantId, now.getYear(), now.getMonthValue())
            .map(r -> ResponseEntity.ok(UsageResponse.from(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/quota/orders")
    public QuotaResponse orderQuota(@RequestParam String tenantId,
                                    @RequestParam(defaultValue = "FREE") String plan) {
        QuotaStatus status = accumulator.checkOrderQuota(tenantId, plan, YearMonth.now());
        return new QuotaResponse(tenantId, status.used(), status.limit(), status.exceeded());
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(@RequestParam String tenantId) {
        return invoiceRepo.findByTenantIdOrderByBillingYearDescBillingMonthDesc(tenantId)
            .stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<InvoiceResponse> invoice(@PathVariable UUID id) {
        return invoiceRepo.findById(id)
            .map(inv -> ResponseEntity.ok(InvoiceResponse.from(inv)))
            .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------------------
    // Admin endpoints (require X-Internal-Token)
    // -----------------------------------------------------------------------

    /** Called by tenant-service when a tenant's plan changes. */
    @PutMapping("/usage/plan")
    public ResponseEntity<Void> syncPlan(
            @RequestBody PlanSyncRequest req,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        requireInternal(token);
        PlanTier tier = PlanTier.valueOf(req.plan().toUpperCase());
        YearMonth now = YearMonth.now();
        usageRepo.findByTenantIdAndBillingYearAndBillingMonth(req.tenantId(), now.getYear(), now.getMonthValue())
            .ifPresent(r -> {
                r.updatePlan(tier);
                usageRepo.save(r);
            });
        return ResponseEntity.noContent().build();
    }

    /** Force-finalize a specific invoice (admin override). */
    @PostMapping("/invoices/{id}/finalize")
    public ResponseEntity<InvoiceResponse> forceFinalize(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        requireInternal(token);
        Invoice inv = invoiceRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invoice not found: " + id));
        if (inv.getStatus() == InvoiceStatus.FINALIZED) {
            throw new ResponseStatusException(CONFLICT, "Invoice already finalized");
        }
        inv.finalize(InvoiceStatus.FINALIZED);
        invoiceRepo.save(inv);
        return ResponseEntity.ok(InvoiceResponse.from(inv));
    }

    private void requireInternal(String token) {
        if (internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ResponseStatusException(FORBIDDEN, "Admin access requires X-Internal-Token");
        }
    }
}

