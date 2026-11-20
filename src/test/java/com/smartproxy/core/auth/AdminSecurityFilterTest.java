package com.smartproxy.core.auth;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.core.foundation.error.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminSecurityFilterTest {

    private SmartProxyProperties properties;
    private AdminSecurityFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SmartProxyProperties();
        properties.getSecurity().getAdmin().setEnabled(true);
        properties.getSecurity().getAdmin().setAdminKey("admin-secret-test-key");
        properties.getSecurity().getAdmin().setHeaderName("X-Admin-Key");

        filter = new AdminSecurityFilter(properties);
    }

    @Test
    void shouldAllowPublicPathBypassingAdminFilter() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldRejectAdminEndpointWithoutAdminKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/routes").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.UNAUTHORIZED)
                .verify();
    }

    @Test
    void shouldAllowAdminEndpointWithValidAdminKey() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/routes")
                .header("X-Admin-Key", "admin-secret-test-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldAllowAdminEndpointWithAdminRoleJwt() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/services").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ClientIdentity adminIdentity = ClientIdentity.authenticated(
                "sys-admin", "system", Set.of("ADMIN"), Set.of("admin:all"), AuthType.JWT, Map.of()
        );
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, adminIdentity);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }
}
