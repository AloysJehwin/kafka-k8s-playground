package com.eventflow.billing.api;

import com.eventflow.billing.domain.Invoice;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.InvoiceRepository;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import com.eventflow.billing.metering.UsageAccumulator;
import com.eventflow.billing.metering.UsageAccumulator.QuotaStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final UsageRecordRepository usageRepo;
    private final InvoiceRepository invoiceRepo;
    private final UsageAccumulator accumulator;

    public BillingController(UsageRecordRepository usageRepo,
                              InvoiceRepository invoiceRepo,
                              UsageAccumulator accumulator) {
        this.usageRepo = usageRepo;
        this.invoiceRepo = invoiceRepo;
        this.accumulator = accumulator;
    }

    // -----------------------------------------------------------------------
    // Response records
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------------

    /** Current and historical usage for a tenant. */
    @GetMapping("/usage")
    public List<UsageResponse> usage(@RequestParam String tenantId) {
        return usageRepo.findByTenantIdOrderByBillingYearDescBillingMonthDesc(tenantId)
            .stream().map(UsageResponse::from).toList();
    }

    /** Current month usage for a tenant. */
    @GetMapping("/usage/current")
    public ResponseEntity<UsageResponse> currentUsage(@RequestParam String tenantId) {
        YearMonth now = YearMonth.now();
        return usageRepo.findByTenantIdAndBillingYearAndBillingMonth(
                tenantId, now.getYear(), now.getMonthValue())
            .map(r -> ResponseEntity.ok(UsageResponse.from(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Quota check — used by API gateway to decide whether to allow a request. */
    @GetMapping("/quota/orders")
    public QuotaResponse orderQuota(@RequestParam String tenantId,
                                    @RequestParam(defaultValue = "FREE") String plan) {
        QuotaStatus status = accumulator.checkOrderQuota(tenantId, plan, YearMonth.now());
        return new QuotaResponse(tenantId, status.used(), status.limit(), status.exceeded());
    }

    /** All invoices for a tenant. */
    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(@RequestParam String tenantId) {
        return invoiceRepo.findByTenantIdOrderByBillingYearDescBillingMonthDesc(tenantId)
            .stream().map(InvoiceResponse::from).toList();
    }

    /** Single invoice. */
    @GetMapping("/invoices/{id}")
    public ResponseEntity<InvoiceResponse> invoice(@PathVariable UUID id) {
        return invoiceRepo.findById(id)
            .map(inv -> ResponseEntity.ok(InvoiceResponse.from(inv)))
            .orElse(ResponseEntity.notFound().build());
    }
}
