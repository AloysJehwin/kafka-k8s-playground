package com.eventflow.webhook.delivery;

import com.eventflow.webhook.domain.DeliveryStatus;
import com.eventflow.webhook.domain.WebhookDelivery;
import com.eventflow.webhook.infrastructure.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);

    private final WebhookDeliveryRepository deliveryRepo;
    private final WebhookDeliveryService deliveryService;

    public WebhookRetryScheduler(WebhookDeliveryRepository deliveryRepo,
                                  WebhookDeliveryService deliveryService) {
        this.deliveryRepo = deliveryRepo;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${eventflow.webhook.retry-interval-ms:30000}")
    @Transactional
    public void retryFailed() {
        List<WebhookDelivery> due = deliveryRepo.findDueForRetry(
            DeliveryStatus.FAILED, Instant.now(), PageRequest.of(0, 50));

        if (!due.isEmpty()) {
            log.info("Retrying {} failed webhook deliveries", due.size());
        }
        for (WebhookDelivery d : due) {
            deliveryService.retry(d);
        }
    }
}
