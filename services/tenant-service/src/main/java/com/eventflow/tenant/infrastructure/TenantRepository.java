package com.eventflow.tenant.infrastructure;

import com.eventflow.tenant.domain.Tenant;
import com.eventflow.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByApiKey(String apiKey);

    List<Tenant> findByStatus(TenantStatus status);
}
