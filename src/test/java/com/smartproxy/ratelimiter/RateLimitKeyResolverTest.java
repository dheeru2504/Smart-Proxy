package com.smartproxy.ratelimiter;

import com.smartproxy.core.auth.AuthType;
import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.model.RouteDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitKeyResolverTest {

    private RateLimitKeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        keyResolver = new RateLimitKeyResolver();
    }

    @Test
    void shouldResolveKeyByIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-Forwarded-For", "203.0.113.195, 10.0.0.1")
                .build();

        String key = keyResolver.resolveKey(request, null, null, "IP");
        assertEquals("203.0.113.195", key);
    }

    @Test
    void shouldResolveKeyByUser() {
        ClientIdentity identity = ClientIdentity.authenticated(
                "user-999", "tenant-corp", Set.of("USER"), Set.of(), AuthType.JWT, Map.of()
        );

        String key = keyResolver.resolveKey(null, null, identity, "USER");
        assertEquals("user-999", key);
    }

    @Test
    void shouldResolveKeyByTenant() {
        ClientIdentity identity = ClientIdentity.authenticated(
                "user-999", "tenant-corp", Set.of("USER"), Set.of(), AuthType.JWT, Map.of()
        );

        String key = keyResolver.resolveKey(null, null, identity, "TENANT");
        assertEquals("tenant-corp", key);
    }

    @Test
    void shouldResolveKeyByRoute() {
        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-service-route")
                .build();

        String key = keyResolver.resolveKey(null, route, null, "ROUTE");
        assertEquals("orders-service-route", key);
    }

    @Test
    void shouldResolveCompositeKey() {
        ClientIdentity identity = ClientIdentity.authenticated(
                "client-1", "tenant-a", Set.of(), Set.of(), AuthType.API_KEY, Map.of()
        );
        RouteDefinition route = RouteDefinition.builder()
                .routeId("checkout-route")
                .build();

        String key = keyResolver.resolveKey(null, route, identity, "COMPOSITE");
        assertEquals("tenant-a:client-1:checkout-route", key);
    }
}
