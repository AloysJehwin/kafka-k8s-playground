package com.eventflow.order.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select o from OutboxEvent o where o.processedAt is null order by o.createdAt asc")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "100"))
    List<OutboxEvent> findUnprocessed(org.springframework.data.domain.Pageable pageable);
}
