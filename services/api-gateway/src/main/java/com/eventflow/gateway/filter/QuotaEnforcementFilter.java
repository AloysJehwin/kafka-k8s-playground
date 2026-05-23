package com.eventflow.gateway.filter;

import com.eventflow.gateway.billing.BillingQuotaClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs after ApiKeyAuthFilter (order=-100) and RateLimitFilter (order=-90).
 * Only checks quota on order-write paths to avoid adding latency to reads.
 */
@Component
public class QuotaEnforcementFilter implements GlobalFilter, Ordered {

    private final BillingQuotaClient billingClient;

    public QuotaEnforcementFilter(BillingQuotaClient billingClient) {
        this.billingClient = billingClient;
    }

    @Override
    public int getOrder() {
        return -80;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Only enforce quota on order placement (POST /api/orders)
        if (!isOrderWrite(path, method)) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR);
        String plan = exchange.getAttribute(ApiKeyAuthFilter.TENANT_PLAN_ATTR);

        if (tenantId == null) {
            return chain.filter(exchange);
        }

        return billingClient.checkOrderQuota(tenantId, plan == null ? "FREE" : plan)
            .flatMap(quota -> {
                if (quota.exceeded()) {
                    return paymentRequired(exchange,
                        "Order quota exceeded: " + quota.used() + "/" + quota.limit() +
                        " orders used this month. Upgrade your plan to continue.");
                }
                return chain.filter(exchange);
            })
            // If billing-service is unreachable, fail open (allow the request)
            .switchIfEmpty(chain.filter(exchange));
    }

    private static boolean isOrderWrite(String path, String method) {
        return path.startsWith("/api/orders") && "POST".equals(method);
    }

    private static Mono<Void> paymentRequired(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYMENT_REQUIRED);
        try {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (UnsupportedOperationException ignored) {}
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
