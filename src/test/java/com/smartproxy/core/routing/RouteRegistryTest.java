package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteRegistryTest {

    @Test
    void shouldMatchExactRoute() {
        RouteDefinition r1 = RouteDefinition.builder()
                .routeId("users-list")
                .pathPattern("/api/v1/users")
                .methods(Set.of("GET"))
                .serviceId("user-service")
                .build();

        RouteDefinition r2 = RouteDefinition.builder()
                .routeId("users-wildcard")
                .pathPattern("/api/v1/**")
                .serviceId("gateway-catchall")
                .build();

        InMemoryRouteRegistry registry = new InMemoryRouteRegistry(List.of(r2, r1));

        Optional<RouteDefinition> matchGet = registry.match("GET", "/api/v1/users");
        assertTrue(matchGet.isPresent());
        assertEquals("users-list", matchGet.get().getRouteId());

        Optional<RouteDefinition> matchPost = registry.match("POST", "/api/v1/users");
        assertTrue(matchPost.isPresent());
        assertEquals("users-wildcard", matchPost.get().getRouteId());
    }

    @Test
    void shouldAtomicallyReloadRoutes() {
        InMemoryRouteRegistry registry = new InMemoryRouteRegistry();

        assertEquals(0, registry.getRoutes().size());
        assertTrue(registry.match("GET", "/api/v1/users").isEmpty());

        RouteDefinition r1 = RouteDefinition.builder()
                .routeId("users-service")
                .pathPattern("/api/v1/users/**")
                .serviceId("user-service")
                .build();

        registry.reload(List.of(r1));

        assertEquals(1, registry.getRoutes().size());
        Optional<RouteDefinition> match = registry.match("GET", "/api/v1/users/123");
        assertTrue(match.isPresent());
        assertEquals("users-service", match.get().getRouteId());
    }
}
