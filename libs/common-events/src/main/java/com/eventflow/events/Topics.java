package com.eventflow.events;

/**
 * Centralised topic names so producers and consumers don't drift.
 * Naming: {domain}.{event} — past-tense for facts.
 */
public final class Topics {

    public static final String ORDERS_PLACED = "orders.placed";
    public static final String PAYMENTS_PROCESSED = "payments.processed";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String ORDERS_COMPLETED = "orders.completed";

    public static final String DLQ_SUFFIX = ".DLT";

    private Topics() {}
}
