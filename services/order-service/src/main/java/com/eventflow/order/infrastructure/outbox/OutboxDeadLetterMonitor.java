package com.eventflow.order.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically scans for dead-letter outbox events (retryCount >= MAX_RETRIES
 * and processedAt IS NULL) and logs them for operator visibility.
 *
 * These events were exhausted by OutboxRelay and need manual intervention
 * or a separate remediation process.
 */
@Component
public class OutboxDeadLetterMonitor {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeadLetterMonitor.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxRepository repository;

    public OutboxDeadLetterMonitor(OutboxRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 60_000)
    public void monitor() {
        var deadLetters = repository.findByRetryCountGreaterThanEqualAndProcessedAtIsNull(
                MAX_RETRIES, PageRequest.of(0, 10));

        if (!deadLetters.isEmpty()) {
            log.warn("Dead outbox events: {}", deadLetters.size());
            for (var event : deadLetters) {
                log.warn("  Dead letter — id={} aggregateId={} topic={} retryCount={} lastError={}",
                         event.getId(), event.getAggregateId(), event.getTopic(),
                         event.getRetryCount(), event.getLastError());
            }
        }
    }
}
