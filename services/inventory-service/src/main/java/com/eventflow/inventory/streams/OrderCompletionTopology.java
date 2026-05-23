package com.eventflow.inventory.streams;

import com.eventflow.events.*;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

/**
 * Joins payments + inventory reservations on orderId. Emits OrderCompleted
 * once both sides agree. CONFIRMED iff payment APPROVED and inventory RESERVED.
 */
@Configuration
public class OrderCompletionTopology {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionTopology.class);

    @Bean
    public KStream<String, OrderCompleted> completionStream(StreamsBuilder builder) {

        var payments = builder.<String, PaymentProcessed>stream(Topics.PAYMENTS_PROCESSED)
            .selectKey((k, v) -> v.getOrderId().toString(), Named.as("payments-by-order"));

        var inventory = builder.<String, InventoryReserved>stream(Topics.INVENTORY_RESERVED)
            .selectKey((k, v) -> v.getOrderId().toString(), Named.as("inventory-by-order"));

        var completed = payments.join(
            inventory,
            (payment, inv) -> {
                var ok = payment.getStatus() == PaymentStatus.APPROVED
                    && inv.getStatus() == InventoryStatus.RESERVED;
                String reason = null;
                if (!ok) {
                    reason = (payment.getStatus() != PaymentStatus.APPROVED)
                        ? "payment_declined"
                        : "out_of_stock";
                }
                log.info("Order completion orderId={} confirmed={}", payment.getOrderId(), ok);
                return OrderCompleted.newBuilder()
                    .setOrderId(payment.getOrderId())
                    .setStatus(ok ? OrderStatus.CONFIRMED : OrderStatus.REJECTED)
                    .setReason(reason)
                    .setCompletedAt(Instant.now())
                    .setCorrelationId(payment.getCorrelationId())
                    .setTenantId(payment.getTenantId())
                    .build();
            },
            JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(5))
        );

        completed.to(Topics.ORDERS_COMPLETED, Produced.as("orders-completed-sink"));
        return completed;
    }
}
