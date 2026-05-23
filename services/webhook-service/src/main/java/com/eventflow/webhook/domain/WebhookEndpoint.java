package com.eventflow.webhook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints",
       indexes = @Index(name = "idx_webhook_tenant", columnList = "tenant_id"))
public class WebhookEndpoint {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secret;

    @Column(name = "event_types", nullable = false)
    private String eventTypes; // CSV: "order.placed,order.completed"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {}

    public WebhookEndpoint(String tenantId, String url, String secret, String eventTypes) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.url = url;
        this.secret = secret;
        this.eventTypes = eventTypes;
        this.status = WebhookStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.status = WebhookStatus.DISABLED;
        this.updatedAt = Instant.now();
    }

    public boolean subscribesTo(String eventType) {
        for (String t : eventTypes.split(",")) {
            if (t.trim().equals(eventType)) return true;
        }
        return false;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public String getEventTypes() { return eventTypes; }
    public WebhookStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
