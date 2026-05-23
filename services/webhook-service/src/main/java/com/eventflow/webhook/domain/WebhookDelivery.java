package com.eventflow.webhook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries",
       indexes = {
           @Index(name = "idx_delivery_endpoint", columnList = "endpoint_id"),
           @Index(name = "idx_delivery_tenant",   columnList = "tenant_id")
       })
public class WebhookDelivery {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookDelivery() {}

    public WebhookDelivery(UUID endpointId, String tenantId, String eventType, String eventId, String payload) {
        this.id = UUID.randomUUID();
        this.endpointId = endpointId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.eventId = eventId;
        this.payload = payload;
        this.status = DeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void recordAttempt(int httpStatus) {
        this.attemptCount++;
        this.lastHttpStatus = httpStatus;
        this.lastError = null;
        this.updatedAt = Instant.now();
        if (httpStatus >= 200 && httpStatus < 300) {
            this.status = DeliveryStatus.DELIVERED;
            this.deliveredAt = Instant.now();
        } else {
            this.status = DeliveryStatus.FAILED;
        }
    }

    public void recordError(String error, Instant retryAt) {
        this.attemptCount++;
        this.lastError = error;
        this.status = DeliveryStatus.FAILED;
        this.nextAttemptAt = retryAt;
        this.updatedAt = Instant.now();
    }

    public void markExhausted() {
        this.status = DeliveryStatus.EXHAUSTED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEndpointId() { return endpointId; }
    public String getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
