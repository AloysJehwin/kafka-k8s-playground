package com.eventflow.webhook.consumer;

import com.eventflow.events.PaymentProcessed;
import com.eventflow.events.PaymentStatus;
import com.eventflow.events.Topics;
import com.eventflow.webhook.delivery.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    private final WebhookDeliveryService deliveryService;

    public PaymentProcessedListener(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = Topics.PAYMENTS_PROCESSED, groupId = "webhook-service")
    public void onPaymentProcessed(PaymentProcessed event) {
        if (event.getStatus() != PaymentStatus.DECLINED) return;

        log.debug("Dispatching webhooks for payment.failed orderId={}", event.getOrderId());

        String payload = """
            {"event":"payment.failed","orderId":"%s","paymentId":"%s","tenantId":"%s","processedAt":"%s"}"""
            .formatted(event.getOrderId(), event.getPaymentId(),
                event.getTenantId(), event.getProcessedAt().toString());

        deliveryService.dispatch(event.getTenantId().toString(), "payment.failed",
            event.getOrderId().toString(), payload);
    }
}
