package com.eventflow.kafka;

import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.core.KafkaOperations;

/**
 * Reusable error handler: exponential backoff + DLT routing.
 * Non-retryable poison pills go straight to DLT; transient errors retry up to N times.
 */
public final class ErrorHandlers {

    private ErrorHandlers() {}

    public static DefaultErrorHandler retryingHandler(KafkaOperations<String, Object> kafkaTemplate) {
        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(30_000);

        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var handler = new DefaultErrorHandler(recoverer, backoff);

        // Non-retryable: deserialisation issues, constraint violations
        handler.addNotRetryableExceptions(
            org.apache.kafka.common.errors.SerializationException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
        );
        return handler;
    }
}
