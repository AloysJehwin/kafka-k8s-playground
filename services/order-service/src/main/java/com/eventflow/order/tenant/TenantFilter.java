package com.eventflow.order.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Tenant-Id";
    private final DataSource dataSource;

    public TenantFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var tenantId = request.getHeader(HEADER);
        try {
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId);
                setDbTenantId(tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
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
