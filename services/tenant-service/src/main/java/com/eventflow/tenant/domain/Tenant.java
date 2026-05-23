package com.eventflow.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(length = 63, unique = true, nullable = false)
    private String slug;

    @Column(length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private TenantPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private TenantStatus status;

    @Column(name = "api_key", length = 64, unique = true, nullable = false)
    private String apiKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
        // JPA
    }

    public Tenant(UUID id, String slug, String name, TenantPlan plan) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.plan = plan;
        this.status = TenantStatus.ACTIVE;
        this.apiKey = UUID.randomUUID().toString().replace("-", "");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void updateName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void updatePlan(TenantPlan plan) {
        this.plan = plan;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public TenantPlan getPlan() { return plan; }
    public TenantStatus getStatus() { return status; }
    public String getApiKey() { return apiKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
