package com.eventflow.billing.domain;

public enum PlanTier {
    FREE,
    STARTER,
    PRO,
    ENTERPRISE;

    public int orderQuota() {
        return switch (this) {
            case FREE       -> 100;
            case STARTER    -> 2_000;
            case PRO        -> 20_000;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    public int webhookQuota() {
        return switch (this) {
            case FREE       -> 0;
            case STARTER    -> 500;
            case PRO        -> 10_000;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }
}
