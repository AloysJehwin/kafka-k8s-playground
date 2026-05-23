package com.eventflow.webhook.infrastructure;

import com.eventflow.webhook.domain.WebhookEndpoint;
import com.eventflow.webhook.domain.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByTenantIdAndStatus(String tenantId, WebhookStatus status);

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, String tenantId);

    List<WebhookEndpoint> findByTenantId(String tenantId);
}
