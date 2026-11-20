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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminRbacTest {

    private SmartProxyProperties properties;
    private AdminSecurityFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SmartProxyProperties();
        properties.getSecurity().getAdmin().setEnabled(true);
        properties.getSecurity().getAdmin().setHeaderName("X-Admin-Key");
        properties.getSecurity().getAdmin().setAdminKey("admin-secret-test-key");

        filter = new AdminSecurityFilter(properties);
    }

    @Test
    void shouldPermitSystemAdminOnAllEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/admin/routes")
                .header("X-Admin-Key", "admin-secret-test-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldPermitAuditorOnGetAndRejectOnPost() {
        ClientIdentity auditor = ClientIdentity.authenticated("auditor-1", "org", Set.of("AUDITOR"), Set.of(), AuthType.JWT, java.util.Collections.emptyMap());

        // 1. GET is permitted
        MockServerHttpRequest getReq = MockServerHttpRequest.get("/api/admin/audit-events").build();
        MockServerWebExchange getExchange = MockServerWebExchange.from(getReq);
        getExchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, auditor);

        StepVerifier.create(filter.filter(getExchange, ex -> Mono.empty()))
                .verifyComplete();

        // 2. POST is forbidden (403)
        MockServerHttpRequest postReq = MockServerHttpRequest.post("/api/admin/routes").build();
        MockServerWebExchange postExchange = MockServerWebExchange.from(postReq);
        postExchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, auditor);

        StepVerifier.create(filter.filter(postExchange, ex -> Mono.empty()))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.FORBIDDEN)
                .verify();
    }

    @Test
    void shouldPermitOperatorOnSyncAndRejectOnRouteMutation() {
        ClientIdentity operator = ClientIdentity.authenticated("ops-1", "org", Set.of("OPERATOR"), Set.of(), AuthType.JWT, java.util.Collections.emptyMap());

        // 1. POST sync is permitted for operator
        MockServerHttpRequest syncReq = MockServerHttpRequest.post("/api/admin/config/sync").build();
        MockServerWebExchange syncExchange = MockServerWebExchange.from(syncReq);
        syncExchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, operator);

        StepVerifier.create(filter.filter(syncExchange, ex -> Mono.empty()))
                .verifyComplete();

        // 2. POST route mutation is forbidden (403) for operator
        MockServerHttpRequest routeReq = MockServerHttpRequest.post("/api/admin/routes").build();
        MockServerWebExchange routeExchange = MockServerWebExchange.from(routeReq);
        routeExchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, operator);

        StepVerifier.create(filter.filter(routeExchange, ex -> Mono.empty()))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.FORBIDDEN)
                .verify();
    }
}
