package com.eventflow.notification.consumer;

import com.eventflow.events.OrderCompleted;
import com.eventflow.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedListener.class);

    /**
     * @RetryableTopic creates orders.completed-retry-0, -1, -2 topics with
     * exponential backoff. After all attempts fail, message lands in
     * orders.completed-dlt for manual inspection.
     */
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30_000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        autoCreateTopics = "true"
    )
    @KafkaListener(topics = Topics.ORDERS_COMPLETED, groupId = "notification-service")
    public void onOrderCompleted(OrderCompleted event,
                                 @Header(name = "kafka_receivedTopic") String topic) {
        log.info("Notify customer for order {} status={} (from topic={})",
            event.getOrderId(), event.getStatus(), topic);

        // Simulated transient failure for demo purposes — real impl would call email/SMS gateway
        if (event.getOrderId().toString().startsWith("fail")) {
            throw new IllegalStateException("Simulated downstream failure for " + event.getOrderId());
        }
    }

    @DltHandler
    public void onDlt(OrderCompleted event,
                      @Header(name = "kafka_receivedTopic") String topic) {
        log.error("DLT received order={} after all retries exhausted (topic={})",
            event.getOrderId(), topic);
        // Real impl: persist to a dead-letter table for ops to inspect
    }
}
