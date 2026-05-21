package com.eventflow.order.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox: events are written in the SAME transaction as the
 * domain change. A separate poller relays them to Kafka. Avoids dual-write.
 */
@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_unprocessed", columnList = "processedAt")
})
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant processedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId,
                       String topic, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markProcessed() { this.processedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getProcessedAt() { return processedAt; }
}
