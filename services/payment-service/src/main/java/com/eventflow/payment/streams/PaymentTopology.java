package com.eventflow.payment.streams;

import com.eventflow.events.OrderPlaced;
import com.eventflow.events.PaymentProcessed;
import com.eventflow.events.PaymentStatus;
import com.eventflow.events.Topics;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Toy payment processor: declines if amount > $1000, otherwise approves.
 * Real implementation would call a payment gateway with circuit breaker.
 */
@Configuration
public class PaymentTopology {

    private static final Logger log = LoggerFactory.getLogger(PaymentTopology.class);
    private static final BigDecimal LIMIT = new BigDecimal("1000.00");

    @Bean
    public org.apache.kafka.streams.kstream.KStream<String, OrderPlaced> paymentStream(
            StreamsBuilder builder) {

        var orders = builder.<String, OrderPlaced>stream(
            Topics.ORDERS_PLACED, Consumed.as("orders-placed-consumer"));

        var payments = orders.mapValues((key, order) -> {
            log.info("Processing payment orderId={} amount={}", order.getOrderId(), order.getAmount());
            var status = order.getAmount().compareTo(LIMIT) > 0
                ? PaymentStatus.DECLINED
                : PaymentStatus.APPROVED;

            return PaymentProcessed.newBuilder()
                .setOrderId(order.getOrderId())
                .setPaymentId(UUID.randomUUID().toString())
                .setStatus(status)
                .setAmount(order.getAmount())
                .setProcessedAt(Instant.now())
                .setCorrelationId(order.getCorrelationId())
                .build();
        }, org.apache.kafka.streams.kstream.Named.as("process-payment"));

        payments.to(Topics.PAYMENTS_PROCESSED, Produced.as("payments-processed-sink"));
        return orders;
    }
}
