package com.eventflow.billing.consumer;

import com.eventflow.billing.metering.UsageAccumulator;
import com.eventflow.events.OrderPlaced;
import com.eventflow.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Component
public class OrderPlacedBillingListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedBillingListener.class);

    private final UsageAccumulator accumulator;

    public OrderPlacedBillingListener(UsageAccumulator accumulator) {
        this.accumulator = accumulator;
    }

    @KafkaListener(topics = Topics.ORDERS_PLACED, groupId = "billing-service")
    public void onOrderPlaced(OrderPlaced event) {
        String tenantId = event.getTenantId() == null ? "" : event.getTenantId().toString();
        if (tenantId.isBlank()) {
            log.warn("OrderPlaced event missing tenantId, skipping metering. orderId={}", event.getOrderId());
            return;
        }
        YearMonth month = YearMonth.from(
            Instant.ofEpochMilli(event.getPlacedAt().toEpochMilli())
                .atOffset(ZoneOffset.UTC));

        // Plan is not carried in the event — billing-service defaults to FREE and lets
        // the tenant-service own plan truth. Tenants on paid plans are set via the
        // admin API (Phase 5). For now, use FREE as a safe default if the tenant has
        // no existing usage record; the plan on the record will be updated when
        // the admin sets it.
        accumulator.recordOrder(tenantId, "FREE", month);
        log.debug("Metered order for tenant={} month={}", tenantId, month);
    }
}
