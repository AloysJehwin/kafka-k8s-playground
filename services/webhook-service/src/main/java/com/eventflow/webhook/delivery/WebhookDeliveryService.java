package com.eventflow.webhook.delivery;

import com.eventflow.webhook.domain.WebhookDelivery;
import com.eventflow.webhook.domain.WebhookEndpoint;
import com.eventflow.webhook.domain.WebhookStatus;
import com.eventflow.webhook.infrastructure.WebhookDeliveryRepository;
import com.eventflow.webhook.infrastructure.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    static final int MAX_ATTEMPTS = 5;
    // Backoff delays in seconds: 30, 60, 120, 300 (between attempts 1-2, 2-3, 3-4, 4-5)
    private static final long[] BACKOFF_SECONDS = {30, 60, 120, 300};

    private final WebhookEndpointRepository endpointRepo;
    private final WebhookDeliveryRepository deliveryRepo;
    private final HttpClient httpClient;

    public WebhookDeliveryService(WebhookEndpointRepository endpointRepo,
                                   WebhookDeliveryRepository deliveryRepo) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    // Constructor for tests — inject custom HttpClient
    WebhookDeliveryService(WebhookEndpointRepository endpointRepo,
                            WebhookDeliveryRepository deliveryRepo,
                            HttpClient httpClient) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
        this.httpClient = httpClient;
    }

    @Transactional
    public void dispatch(String tenantId, String eventType, String eventId, String payload) {
        List<WebhookEndpoint> endpoints =
            endpointRepo.findByTenantIdAndStatus(tenantId, WebhookStatus.ACTIVE);

        for (WebhookEndpoint ep : endpoints) {
            if (!ep.subscribesTo(eventType)) continue;
            WebhookDelivery delivery = new WebhookDelivery(ep.getId(), tenantId, eventType, eventId, payload);
            deliveryRepo.save(delivery);
            attempt(ep, delivery);
        }
    }

    // Called by scheduler for retries of FAILED deliveries
    @Transactional
    public void retry(WebhookDelivery delivery) {
        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.markExhausted();
            deliveryRepo.save(delivery);
            return;
        }
        endpointRepo.findById(delivery.getEndpointId()).ifPresent(ep -> attempt(ep, delivery));
    }

    private void attempt(WebhookEndpoint ep, WebhookDelivery delivery) {
        String signature = hmacSha256(ep.getSecret(), delivery.getPayload());
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ep.getUrl()))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-EventFlow-Event",     delivery.getEventType())
            .header("X-EventFlow-Delivery",  delivery.getId().toString())
            .header("X-EventFlow-Signature", "sha256=" + signature)
            .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload(), StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            delivery.recordAttempt(status);
            if (status >= 200 && status < 300) {
                log.info("Webhook delivered: endpoint={} delivery={} status={}",
                    ep.getId(), delivery.getId(), status);
            } else {
                scheduleRetry(delivery);
                log.warn("Webhook delivery failed with HTTP {}: endpoint={} delivery={} attempt={}",
                    status, ep.getId(), delivery.getId(), delivery.getAttemptCount());
            }
        } catch (Exception e) {
            Instant retryAt = nextRetryTime(delivery.getAttemptCount());
            delivery.recordError(e.getMessage(), retryAt);
            log.warn("Webhook delivery error: endpoint={} delivery={} attempt={} error={}",
                ep.getId(), delivery.getId(), delivery.getAttemptCount(), e.getMessage());
        }
        deliveryRepo.save(delivery);
    }

    private void scheduleRetry(WebhookDelivery delivery) {
        if (delivery.getAttemptCount() < MAX_ATTEMPTS) {
            // nextAttemptAt is informational; the scheduler polls FAILED deliveries
            delivery.recordError("HTTP " + delivery.getLastHttpStatus(), nextRetryTime(delivery.getAttemptCount()));
        } else {
            delivery.markExhausted();
        }
    }

    private Instant nextRetryTime(int attemptsSoFar) {
        int idx = Math.min(attemptsSoFar, BACKOFF_SECONDS.length - 1);
        return Instant.now().plusSeconds(BACKOFF_SECONDS[idx]);
    }

    public static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
