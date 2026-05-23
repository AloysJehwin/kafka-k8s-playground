package com.eventflow.gateway.filter;

import com.eventflow.gateway.tenant.TenantLookupClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-Api-Key";
    static final String TENANT_ID_ATTR = "tenantId";
    static final String TENANT_PLAN_ATTR = "tenantPlan";

    private final TenantLookupClient tenantLookup;
    private final String internalToken;

    public ApiKeyAuthFilter(TenantLookupClient tenantLookup,
                            @Value("${eventflow.internal-token:}") String internalToken) {
        this.tenantLookup = tenantLookup;
        this.internalToken = internalToken;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return unauthorized(exchange, "Missing X-Api-Key header");
        }

        Mono<TenantLookupClient.TenantInfo> resolved = tenantLookup.resolve(apiKey).cache();

        return resolved.hasElement().flatMap(found -> {
            if (!found) {
                return unauthorized(exchange, "Invalid API key");
            }
            return resolved.flatMap(tenant -> {
                if (!"ACTIVE".equals(tenant.status())) {
                    return unauthorized(exchange, "Tenant is not active");
                }
                exchange.getAttributes().put(TENANT_ID_ATTR, tenant.id());
                exchange.getAttributes().put(TENANT_PLAN_ATTR, tenant.plan());

                var mutated = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", tenant.id())
                    .header("X-Internal-Token", internalToken)
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            });
        });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        try {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (UnsupportedOperationException ignored) {
            // MockServerWebExchange may return read-only headers; content-type is optional
        }
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes();
        var buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
