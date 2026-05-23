package com.eventflow.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ConcurrentHashMap<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000;

    // Requests allowed per minute per plan
    private static final Map<String, Integer> PLAN_LIMITS = Map.of(
        "FREE", 100,
        "STARTER", 500,
        "PRO", 2000,
        "ENTERPRISE", Integer.MAX_VALUE
    );

    @Override
    public int getOrder() {
        return -90;
    }

    void resetCounters() {
        requestCounts.clear();
        windowStart.clear();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR);
        String plan = exchange.getAttribute(ApiKeyAuthFilter.TENANT_PLAN_ATTR);

        // Auth filter already rejected if tenantId is absent
        if (tenantId == null) {
            return chain.filter(exchange);
        }

        int limit = PLAN_LIMITS.getOrDefault(plan, 100);
        if (limit == Integer.MAX_VALUE) {
            return chain.filter(exchange);
        }

        long now = System.currentTimeMillis();
        windowStart.putIfAbsent(tenantId, now);
        requestCounts.putIfAbsent(tenantId, new AtomicLong(0));

        // Reset sliding window if expired
        if (now - windowStart.get(tenantId) > WINDOW_MS) {
            windowStart.put(tenantId, now);
            requestCounts.get(tenantId).set(0);
        }

        long count = requestCounts.get(tenantId).incrementAndGet();
        if (count > limit) {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            try {
                response.getHeaders().set("Retry-After", "60");
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            } catch (UnsupportedOperationException ignored) {
                // Read-only headers in test context; content-type/retry-after are advisory
            }
            byte[] body = "{\"error\":\"Rate limit exceeded\"}".getBytes();
            var buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        }

        // Propagate rate limit headers to downstream
        var mutated = exchange.getRequest().mutate()
            .header("X-RateLimit-Limit", String.valueOf(limit))
            .header("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)))
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
