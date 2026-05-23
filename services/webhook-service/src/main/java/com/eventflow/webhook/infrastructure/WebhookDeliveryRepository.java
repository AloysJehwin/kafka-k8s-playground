package com.eventflow.webhook.infrastructure;

import com.eventflow.webhook.domain.DeliveryStatus;
import com.eventflow.webhook.domain.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<WebhookDelivery> findByEndpointId(UUID endpointId);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status = :status " +
           "AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now) " +
           "AND d.attemptCount < 5")
    List<WebhookDelivery> findDueForRetry(@Param("status") DeliveryStatus status,
                                          @Param("now") Instant now,
                                          Pageable pageable);
}
