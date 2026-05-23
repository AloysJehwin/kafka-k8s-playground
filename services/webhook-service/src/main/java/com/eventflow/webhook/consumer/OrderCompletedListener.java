package com.eventflow.webhook.consumer;

import com.eventflow.events.OrderCompleted;
import com.eventflow.events.OrderStatus;
import com.eventflow.events.Topics;
import com.eventflow.webhook.delivery.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedListener.class);

    private final WebhookDeliveryService deliveryService;

    public OrderCompletedListener(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = Topics.ORDERS_COMPLETED, groupId = "webhook-service")
    public void onOrderCompleted(OrderCompleted event) {
        String eventType = event.getStatus() == OrderStatus.CONFIRMED
            ? "order.completed" : "order.rejected";
        log.debug("Dispatching webhooks for {} orderId={}", eventType, event.getOrderId());

        String payload = """
            {"event":"%s","orderId":"%s","status":"%s","tenantId":"%s","completedAt":"%s"}"""
            .formatted(eventType, event.getOrderId(), event.getStatus(),
                event.getTenantId(), event.getCompletedAt().toString());

        deliveryService.dispatch(event.getTenantId().toString(), eventType,
            event.getOrderId().toString(), payload);
    }
}
