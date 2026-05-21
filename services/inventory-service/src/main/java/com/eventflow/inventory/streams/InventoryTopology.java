package com.eventflow.inventory.streams;

import com.eventflow.events.*;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Maintains stock levels in a state store. On OrderPlaced, attempts to reserve;
 * emits InventoryReserved (RESERVED or OUT_OF_STOCK).
 *
 * State store starts empty — in real life, seed from a stock topic / DB.
 */
@Configuration
public class InventoryTopology {

    private static final Logger log = LoggerFactory.getLogger(InventoryTopology.class);
    static final String STOCK_STORE = "stock-by-product";
    static final int DEFAULT_STOCK = 100; // demo seed

    @Bean
    public KStream<String, OrderPlaced> inventoryStream(StreamsBuilder builder) {

        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(STOCK_STORE),
            Serdes.String(),
            Serdes.Integer()
        ));

        var orders = builder.<String, OrderPlaced>stream(Topics.ORDERS_PLACED);

        var reservations = orders
            .selectKey((k, v) -> v.getProductId().toString(), Named.as("rekey-by-product"))
            .transformValues(InventoryReserver::new, STOCK_STORE);

        reservations.to(Topics.INVENTORY_RESERVED, Produced.as("inventory-reserved-sink"));
        return orders;
    }

    /**
     * ValueTransformer with state store access — atomic reserve operation.
     */
    static class InventoryReserver implements ValueTransformerWithKey<String, OrderPlaced, InventoryReserved> {
        private KeyValueStore<String, Integer> store;

        @Override
        public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
            this.store = context.getStateStore(STOCK_STORE);
        }

        @Override
        public InventoryReserved transform(String productId, OrderPlaced order) {
            Integer current = store.get(productId);
            if (current == null) current = DEFAULT_STOCK;

            var status = current >= order.getQuantity()
                ? InventoryStatus.RESERVED
                : InventoryStatus.OUT_OF_STOCK;

            if (status == InventoryStatus.RESERVED) {
                store.put(productId, current - order.getQuantity());
            }

            log.info("Inventory orderId={} product={} qty={} status={} remaining={}",
                order.getOrderId(), productId, order.getQuantity(), status,
                status == InventoryStatus.RESERVED ? current - order.getQuantity() : current);

            return InventoryReserved.newBuilder()
                .setOrderId(order.getOrderId())
                .setProductId(productId)
                .setQuantity(order.getQuantity())
                .setStatus(status)
                .setReservedAt(Instant.now())
                .setCorrelationId(order.getCorrelationId())
                .build();
        }

        @Override public void close() {}
    }
}
