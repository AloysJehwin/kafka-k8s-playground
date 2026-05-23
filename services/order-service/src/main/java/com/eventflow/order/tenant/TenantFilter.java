package com.eventflow.order.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final DataSource dataSource;
    private final String internalToken;

    public TenantFilter(DataSource dataSource,
                        @Value("${eventflow.internal-token:}") String internalToken) {
        this.dataSource = dataSource;
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var tenantId = request.getHeader(TENANT_HEADER);
        var tokenHeader = request.getHeader(INTERNAL_TOKEN_HEADER);

        // Internal service-to-service calls (outbox relay, etc.) carry X-Internal-Token
        // and operate without a tenant context — let them through unconditionally.
        boolean isInternalCall = tokenHeader != null && !tokenHeader.isBlank()
                && !internalToken.isBlank() && internalToken.equals(tokenHeader);

        // Actuator endpoints are infrastructure paths — never require a tenant header.
        boolean isActuator = request.getRequestURI().startsWith("/actuator");

        try {
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId);
                setDbTenantId(tenantId);
                chain.doFilter(request, response);
            } else if (isInternalCall || isActuator) {
                // No tenant context needed — proceed without setting app.tenant_id
                chain.doFilter(request, response);
            } else {
                // Any request that reaches the service without a tenant header did not
                // pass through the API Gateway (which always injects X-Tenant-Id).
                // Reject it to prevent unauthenticated cross-tenant data leakage.
                logger.warn("Rejected request to " + request.getRequestURI()
                        + " — X-Tenant-Id header missing");
                sendForbidden(response);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void sendForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"X-Tenant-Id header required\"}");
    }

    private void setDbTenantId(String tenantId) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            stmt.setString(1, tenantId);
            stmt.execute();
        } catch (SQLException e) {
            logger.warn("Could not set app.tenant_id session variable: " + e.getMessage());
        }
    }
}
