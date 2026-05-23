package com.eventflow.billing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Monotonically-increasing counters for a tenant's usage within one calendar month.
 * Columns are incremented via UPDATE ... SET col = col + 1 to avoid lost-update races.
 */
@Entity
@Table(name = "usage_records",
       uniqueConstraints = @UniqueConstraint(name = "uq_usage_tenant_month",
                                             columnNames = {"tenant_id", "billing_year", "billing_month"}),
       indexes = @Index(name = "idx_usage_tenant", columnList = "tenant_id"))
public class UsageRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanTier plan;

    @Column(name = "billing_year", nullable = false)
    private int billingYear;

    @Column(name = "billing_month", nullable = false)
    private int billingMonth;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "webhook_delivery_count", nullable = false)
    private long webhookDeliveryCount;

    @Column(name = "api_call_count", nullable = false)
    private long apiCallCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UsageRecord() {}

    public UsageRecord(String tenantId, PlanTier plan, YearMonth month) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.plan = plan;
        this.billingYear = month.getYear();
        this.billingMonth = month.getMonthValue();
        this.orderCount = 0;
        this.webhookDeliveryCount = 0;
        this.apiCallCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementOrders(long delta) {
        this.orderCount += delta;
        this.updatedAt = Instant.now();
    }

    public void incrementWebhookDeliveries(long delta) {
        this.webhookDeliveryCount += delta;
        this.updatedAt = Instant.now();
    }

    public void incrementApiCalls(long delta) {
        this.apiCallCount += delta;
        this.updatedAt = Instant.now();
    }

    public boolean isOrderQuotaExceeded() {
        return orderCount >= plan.orderQuota();
    }

    public boolean isWebhookQuotaExceeded() {
        return webhookDeliveryCount >= plan.webhookQuota();
    }

    public void updatePlan(PlanTier plan) {
        this.plan = plan;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public PlanTier getPlan() { return plan; }
    public int getBillingYear() { return billingYear; }
    public int getBillingMonth() { return billingMonth; }
    public long getOrderCount() { return orderCount; }
    public long getWebhookDeliveryCount() { return webhookDeliveryCount; }
    public long getApiCallCount() { return apiCallCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
