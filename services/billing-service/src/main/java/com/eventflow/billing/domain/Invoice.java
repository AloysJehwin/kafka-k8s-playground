package com.eventflow.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Monthly invoice generated from a UsageRecord once the billing period closes.
 */
@Entity
@Table(name = "invoices",
       uniqueConstraints = @UniqueConstraint(name = "uq_invoice_tenant_month",
                                             columnNames = {"tenant_id", "billing_year", "billing_month"}),
       indexes = @Index(name = "idx_invoice_tenant", columnList = "tenant_id"))
public class Invoice {

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

    /** Total amount in USD cents */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Invoice() {}

    public Invoice(UsageRecord usage) {
        this.id = UUID.randomUUID();
        this.tenantId = usage.getTenantId();
        this.plan = usage.getPlan();
        this.billingYear = usage.getBillingYear();
        this.billingMonth = usage.getBillingMonth();
        this.orderCount = usage.getOrderCount();
        this.webhookDeliveryCount = usage.getWebhookDeliveryCount();
        this.apiCallCount = usage.getApiCallCount();
        this.amountCents = calculateAmountCents(usage);
        this.status = InvoiceStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void finalize(InvoiceStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /** Simple tiered pricing in USD cents. */
    private static long calculateAmountCents(UsageRecord u) {
        long base = switch (u.getPlan()) {
            case FREE       -> 0L;
            case STARTER    -> 2900L;
            case PRO        -> 9900L;
            case ENTERPRISE -> 49900L;
        };
        // overage: $0.01 per order over quota
        long quota = u.getPlan().orderQuota();
        long overage = u.getPlan() != PlanTier.ENTERPRISE && u.getOrderCount() > quota
            ? (u.getOrderCount() - quota)
            : 0L;
        return base + overage;
    }

    public BigDecimal getAmountUsd() {
        return BigDecimal.valueOf(amountCents, 2);
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public PlanTier getPlan() { return plan; }
    public int getBillingYear() { return billingYear; }
    public int getBillingMonth() { return billingMonth; }
    public long getOrderCount() { return orderCount; }
    public long getWebhookDeliveryCount() { return webhookDeliveryCount; }
    public long getApiCallCount() { return apiCallCount; }
    public long getAmountCents() { return amountCents; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
