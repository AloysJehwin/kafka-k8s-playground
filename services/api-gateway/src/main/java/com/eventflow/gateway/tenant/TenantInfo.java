package com.eventflow.gateway.tenant;

/**
 * Minimal tenant projection returned by the tenant-service lookup endpoint.
 */
public record TenantInfo(
        String id,
        String slug,
        String plan,
        String status
) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /** Rate-limit ceiling for this tenant's plan (requests per minute). */
    public int requestsPerMinute() {
        return switch (plan == null ? "FREE" : plan.toUpperCase()) {
            case "ENTERPRISE" -> 1000;
            case "PRO"        -> 500;
            default           -> 100; // FREE
        };
    }
}
