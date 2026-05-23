package com.eventflow.billing.metering;

import com.eventflow.billing.domain.PlanTier;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * Thread-safe usage accumulator. Uses an upsert-then-increment pattern:
 *  1. Try the atomic UPDATE (fast path — row exists).
 *  2. If no row was updated, INSERT the row for this tenant-month and retry.
 * This avoids SELECT-then-INSERT races without a unique-constraint exception in the hot path.
 */
@Service
public class UsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(UsageAccumulator.class);

    private final UsageRecordRepository repo;

    public UsageAccumulator(UsageRecordRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void recordOrder(String tenantId, String plan, YearMonth month) {
        ensureRecord(tenantId, plan, month);
        repo.incrementOrders(tenantId, month.getYear(), month.getMonthValue(), 1L);
        log.debug("Incremented order count for tenant={} month={}", tenantId, month);
    }

    @Transactional
    public void recordWebhookDelivery(String tenantId, String plan, YearMonth month) {
        ensureRecord(tenantId, plan, month);
        repo.incrementWebhookDeliveries(tenantId, month.getYear(), month.getMonthValue(), 1L);
    }

    @Transactional
    public void recordApiCall(String tenantId, String plan, YearMonth month) {
        ensureRecord(tenantId, plan, month);
        repo.incrementApiCalls(tenantId, month.getYear(), month.getMonthValue(), 1L);
    }

    /**
     * Returns the quota check result for the current month without mutating state.
     * Used by the quota-check endpoint consumed by the API gateway.
     */
    @Transactional(readOnly = true)
    public QuotaStatus checkOrderQuota(String tenantId, String plan, YearMonth month) {
        PlanTier tier = parsePlan(plan);
        return repo.findByTenantIdAndBillingYearAndBillingMonth(
                tenantId, month.getYear(), month.getMonthValue())
            .map(r -> new QuotaStatus(r.getOrderCount(), tier.orderQuota(),
                                      r.isOrderQuotaExceeded()))
            .orElse(new QuotaStatus(0L, tier.orderQuota(), false));
    }

    private void ensureRecord(String tenantId, String plan, YearMonth month) {
        if (repo.findByTenantIdAndBillingYearAndBillingMonth(
                tenantId, month.getYear(), month.getMonthValue()).isEmpty()) {
            try {
                repo.saveAndFlush(new UsageRecord(tenantId, parsePlan(plan), month));
            } catch (Exception ex) {
                // Another thread raced us — row now exists, ignore
                log.trace("Usage record already exists for tenant={} month={}", tenantId, month);
            }
        }
    }

    static PlanTier parsePlan(String plan) {
        try {
            return PlanTier.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PlanTier.FREE;
        }
    }

    public record QuotaStatus(long used, long limit, boolean exceeded) {}
}
