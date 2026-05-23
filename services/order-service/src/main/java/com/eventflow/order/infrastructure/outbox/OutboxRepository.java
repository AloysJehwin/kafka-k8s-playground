package com.eventflow.order.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select o from OutboxEvent o where o.processedAt is null order by o.createdAt asc")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "100"))
    List<OutboxEvent> findUnprocessed(Pageable pageable);

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processedAt IS NULL")
    long countUnprocessed();

    List<OutboxEvent> findByRetryCountGreaterThanEqualAndProcessedAtIsNull(int retryCount, Pageable pageable);
}
