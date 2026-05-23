package com.eventflow.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests that don't carry the internal service token.
 * The BFF sets X-Internal-Token on every downstream call; anything hitting
 * order-service directly (port-forwarded, exposed node port, etc.) is denied.
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";

    @Value("${eventflow.internal-token:#{null}}")
    private String expectedToken;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Actuator health probes must always pass
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // If no token is configured (local dev without the env var), allow all
        if (expectedToken == null || expectedToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (!expectedToken.equals(provided)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing or invalid internal token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
