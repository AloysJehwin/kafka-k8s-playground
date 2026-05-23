package com.eventflow.webbff.sse;

import com.eventflow.events.OrderCompleted;
import com.eventflow.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OrderCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedListener.class);

    private final OrderEventBroker broker;
    private final RestClient orderClient;

    public OrderCompletedListener(OrderEventBroker broker,
                                  @Value("${eventflow.order-service-url}") String orderServiceUrl) {
        this.broker = broker;
        this.orderClient = RestClient.builder().baseUrl(orderServiceUrl).build();
    }

    @KafkaListener(
        topics = Topics.ORDERS_COMPLETED,
        groupId = "web-bff-sse",
        containerFactory = "orderCompletedListenerFactory"
    )
    public void onOrderCompleted(OrderCompleted event) {
        var orderId = event.getOrderId().toString();
        log.info("BFF received completion orderId={} status={}", orderId, event.getStatus());

        try {
            var detail = orderClient.get()
                .uri("/api/orders/{id}", orderId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (detail == null) return;
            var customerId = (String) detail.get("customerId");
            if (customerId == null) return;

            broker.broadcast(customerId, Map.of(
                "orderId", orderId,
                "status", event.getStatus().toString(),
                "reason", event.getReason() == null ? null : event.getReason().toString(),
                "completedAt", event.getCompletedAt().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to broadcast SSE for orderId={}: {}", orderId, e.getMessage());
        }
    }
}
