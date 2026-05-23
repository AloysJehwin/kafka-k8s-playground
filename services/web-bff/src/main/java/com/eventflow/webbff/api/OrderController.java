package com.eventflow.webbff.api;

import com.eventflow.webbff.security.TenantResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final RestClient orderClient;
    private final TenantResolver tenantResolver;

    public OrderController(
            @Value("${eventflow.order-service-url}") String orderServiceUrl,
            @Value("${eventflow.internal-token:}") String internalToken,
            TenantResolver tenantResolver) {
        this.orderClient = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(orderServiceUrl)
            .defaultHeader("X-Internal-Token", internalToken)
            .build();
        this.tenantResolver = tenantResolver;
    }

    public record PlaceOrderRequest(
        @NotBlank String productId,
        @Positive int quantity,
        @Positive BigDecimal amount,
        @NotBlank String currency
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> place(
            @Valid @RequestBody PlaceOrderRequest req,
            @AuthenticationPrincipal OAuth2User user) {

        var customerId = user.<String>getAttribute("sub");
        var tenantId = tenantResolver.resolve(user);
        var payload = Map.of(
            "customerId", customerId,
            "productId", req.productId(),
            "quantity", req.quantity(),
            "amount", req.amount(),
            "currency", req.currency()
        );

        return orderClient.post()
            .uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Tenant-Id", tenantId)
            .body(payload)
            .retrieve()
            .toEntity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User user) {
        var customerId = user.<String>getAttribute("sub");
        var tenantId = tenantResolver.resolve(user);
        return orderClient.get()
            .uri("/api/orders/{id}", id)
            .header("X-Customer-Id", customerId)
            .header("X-Tenant-Id", tenantId)
            .retrieve()
            .toEntity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping
    public List<Map<String, Object>> myOrders(@AuthenticationPrincipal OAuth2User user) {
        var customerId = user.<String>getAttribute("sub");
        var tenantId = tenantResolver.resolve(user);
        return orderClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/orders").queryParam("customerId", customerId).build())
            .header("X-Tenant-Id", tenantId)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }
}
