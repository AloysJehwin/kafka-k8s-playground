package com.eventflow.order.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock
    DataSource dataSource;

    @Mock
    Connection connection;

    @Mock
    PreparedStatement preparedStatement;

    // Internal token used across tests that need it
    private static final String VALID_INTERNAL_TOKEN = "secret-relay-token";

    private TenantFilter filterWithToken;
    private TenantFilter filterNoToken;

    @BeforeEach
    void setUp() throws Exception {
        // Wire the DataSource mock so set_config calls don't NPE
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.execute()).thenReturn(true);

        filterWithToken = new TenantFilter(dataSource, VALID_INTERNAL_TOKEN);
        filterNoToken   = new TenantFilter(dataSource, "");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // 1. X-Tenant-Id present — context is set and set_config is called
    // ------------------------------------------------------------------

    @Test
    void requestWithTenantHeader_setsTenantContext() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Tenant-Id", "acme");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filterWithToken.doFilter(request, response, chain);

        // TenantContext.get() is called inside the filter execution; after the chain
        // completes we capture what was set by verifying the DB call was made and
        // that the chain was invoked (meaning no early 403).
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was called

        // set_config must have been called on the DataSource
        verify(dataSource).getConnection();
        verify(preparedStatement).setString(1, "acme");
        verify(preparedStatement).execute();
    }

    // ------------------------------------------------------------------
    // 2. No X-Tenant-Id, no internal token → 403
    // ------------------------------------------------------------------

    @Test
    void requestWithoutTenantHeader_returns403() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/orders");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filterNoToken.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("X-Tenant-Id header required");
        // Chain must NOT have been invoked
        assertThat(chain.getRequest()).isNull();
        // set_config must NOT have been called
        verifyNoInteractions(dataSource);
    }

    // ------------------------------------------------------------------
    // 3. X-Internal-Token present, no X-Tenant-Id → passes through
    // ------------------------------------------------------------------

    @Test
    void requestWithInternalToken_allowsWithoutTenantId() throws Exception {
        var request  = new MockHttpServletRequest("POST", "/api/internal/outbox");
        request.addHeader("X-Internal-Token", VALID_INTERNAL_TOKEN);
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filterWithToken.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was called
        // No tenant context should be set — no set_config call
        verifyNoInteractions(dataSource);
    }

    // ------------------------------------------------------------------
    // 4. /actuator/** always passes regardless of headers
    // ------------------------------------------------------------------

    @Test
    void actuatorRequest_alwaysPasses() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filterNoToken.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was called
        verifyNoInteractions(dataSource);
    }
}
