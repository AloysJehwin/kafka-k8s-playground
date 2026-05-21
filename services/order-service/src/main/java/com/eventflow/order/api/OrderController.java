package com.eventflow.order.api;

import com.eventflow.events.OrderPlaced;
import com.eventflow.events.Topics;
import com.eventflow.order.domain.Order;
import com.eventflow.order.infrastructure.outbox.OutboxEvent;
import com.eventflow.order.infrastructure.outbox.OutboxRepository;
import com.eventflow.order.infrastructure.persistence.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orders;
    private final OutboxRepository outbox;

    public OrderController(OrderRepository orders, OutboxRepository outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotBlank String productId,
        @Positive int quantity,
        @Positive BigDecimal amount,
        @NotBlank String currency
    ) {}

    public record OrderResponse(UUID id, String status) {}

    /**
     * Order placement: writes Order + OutboxEvent atomically. The relay later
     * publishes to Kafka. Caller gets a 201 immediately — eventually consistent.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest req) {
        var orderId = UUID.randomUUID();
        var correlationId = UUID.randomUUID().toString();

        var order = new Order(orderId, req.customerId(), req.productId(),
            req.quantity(), req.amount(), req.currency());
        orders.save(order);

        var event = OrderPlaced.newBuilder()
            .setOrderId(orderId.toString())
            .setCustomerId(req.customerId())
            .setProductId(req.productId())
            .setQuantity(req.quantity())
            .setAmount(req.amount())
            .setCurrency(req.currency())
            .setPlacedAt(Instant.now())
            .setCorrelationId(correlationId)
            .build();

        outbox.save(new OutboxEvent(
            "Order", orderId.toString(),
            Topics.ORDERS_PLACED, "OrderPlaced",
            encode(event)
        ));

        log.info("Order placed orderId={} correlationId={}", orderId, correlationId);
        return ResponseEntity.created(URI.create("/api/orders/" + orderId))
            .body(new OrderResponse(orderId, order.getStatus().name()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return orders.findById(id)
            .map(o -> ResponseEntity.ok(new OrderResponse(o.getId(), o.getStatus().name())))
            .orElse(ResponseEntity.notFound().build());
    }

    private String encode(OrderPlaced event) {
        try {
            var out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter<>(OrderPlaced.class).write(event, encoder);
            encoder.flush();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode OrderPlaced", e);
        }
    }
}
