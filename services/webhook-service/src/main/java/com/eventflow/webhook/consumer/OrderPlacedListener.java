package com.eventflow.webhook.consumer;

import com.eventflow.events.OrderPlaced;
import com.eventflow.events.Topics;
import com.eventflow.webhook.delivery.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);

    private final WebhookDeliveryService deliveryService;

    public OrderPlacedListener(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = Topics.ORDERS_PLACED, groupId = "webhook-service")
    public void onOrderPlaced(OrderPlaced event) {
        log.debug("Dispatching webhooks for order.placed orderId={} tenantId={}",
            event.getOrderId(), event.getTenantId());

        String payload = """
            {"event":"order.placed","orderId":"%s","customerId":"%s","productId":"%s",\
"quantity":%d,"currency":"%s","tenantId":"%s","placedAt":"%s"}"""
            .formatted(
                event.getOrderId(), event.getCustomerId(), event.getProductId(),
                event.getQuantity(), event.getCurrency(), event.getTenantId(),
                event.getPlacedAt().toString()
            );

        deliveryService.dispatch(event.getTenantId().toString(), "order.placed",
            event.getOrderId().toString(), payload);
    }
}
