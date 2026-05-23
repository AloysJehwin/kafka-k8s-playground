package com.eventflow.webhook.api;

import com.eventflow.webhook.domain.DeliveryStatus;
import com.eventflow.webhook.domain.WebhookDelivery;
import com.eventflow.webhook.domain.WebhookEndpoint;
import com.eventflow.webhook.domain.WebhookStatus;
import com.eventflow.webhook.infrastructure.WebhookDeliveryRepository;
import com.eventflow.webhook.infrastructure.WebhookEndpointRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@Validated
public class WebhookController {

    private final WebhookEndpointRepository endpointRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    public WebhookController(WebhookEndpointRepository endpointRepo,
                              WebhookDeliveryRepository deliveryRepo) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
    }

    // -----------------------------------------------------------------------
    // Request / Response records
    // -----------------------------------------------------------------------

    record RegisterRequest(
        @NotBlank String tenantId,
        @NotBlank @Pattern(regexp = "https?://.+") String url,
        @NotBlank @Size(min = 16) String secret,
        @NotBlank String eventTypes
    ) {}

    record EndpointResponse(
        UUID id, String tenantId, String url, String eventTypes,
        String status, Instant createdAt
    ) {
        static EndpointResponse from(WebhookEndpoint e) {
            return new EndpointResponse(e.getId(), e.getTenantId(), e.getUrl(),
                e.getEventTypes(), e.getStatus().name(), e.getCreatedAt());
        }
    }

    record DeliveryResponse(
        UUID id, UUID endpointId, String eventType, String eventId,
        String status, int attemptCount, Integer lastHttpStatus,
        String lastError, Instant deliveredAt, Instant createdAt
    ) {
        static DeliveryResponse from(WebhookDelivery d) {
            return new DeliveryResponse(d.getId(), d.getEndpointId(), d.getEventType(),
                d.getEventId(), d.getStatus().name(), d.getAttemptCount(),
                d.getLastHttpStatus(), d.getLastError(), d.getDeliveredAt(), d.getCreatedAt());
        }
    }

    // -----------------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<EndpointResponse> register(
            @Valid @RequestBody RegisterRequest req,
            UriComponentsBuilder ucb) {

        var endpoint = new WebhookEndpoint(req.tenantId(), req.url(), req.secret(), req.eventTypes());
        endpointRepo.save(endpoint);
        URI location = ucb.path("/api/webhooks/{id}").buildAndExpand(endpoint.getId()).toUri();
        return ResponseEntity.created(location).body(EndpointResponse.from(endpoint));
    }

    @GetMapping
    public List<EndpointResponse> list(@RequestParam String tenantId) {
        return endpointRepo.findByTenantId(tenantId).stream()
            .map(EndpointResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EndpointResponse> get(@PathVariable UUID id,
                                                 @RequestParam String tenantId) {
        return endpointRepo.findByIdAndTenantId(id, tenantId)
            .map(e -> ResponseEntity.ok(EndpointResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @RequestParam String tenantId) {
        var found = endpointRepo.findByIdAndTenantId(id, tenantId);
        if (found.isEmpty()) return ResponseEntity.<Void>notFound().build();
        found.get().disable();
        endpointRepo.save(found.get());
        return ResponseEntity.<Void>noContent().build();
    }

    @GetMapping("/deliveries")
    public List<DeliveryResponse> deliveries(@RequestParam String tenantId) {
        return deliveryRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(DeliveryResponse::from)
            .toList();
    }

    @GetMapping("/{id}/deliveries")
    public List<DeliveryResponse> endpointDeliveries(@PathVariable UUID id,
                                                      @RequestParam String tenantId) {
        endpointRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));
        return deliveryRepo.findByEndpointId(id).stream()
            .map(DeliveryResponse::from)
            .toList();
    }
}
