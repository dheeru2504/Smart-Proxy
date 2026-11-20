package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteComparatorTest {

    @Test
    void shouldPrioritizeExplicitOrder() {
        RouteDefinition r1 = RouteDefinition.builder().routeId("r1").pathPattern("/api/**").order(10).build();
        RouteDefinition r2 = RouteDefinition.builder().routeId("r2").pathPattern("/api/**").order(5).build();

        List<RouteDefinition> list = new ArrayList<>(List.of(r1, r2));
        list.sort(RouteComparator.INSTANCE);

        assertEquals("r2", list.get(0).getRouteId());
        assertEquals("r1", list.get(1).getRouteId());
    }

    @Test
    void shouldPrioritizeExactMatchOverWildcard() {
        RouteDefinition exact = RouteDefinition.builder().routeId("exact").pathPattern("/api/v1/users").order(0).build();
        RouteDefinition wildcard = RouteDefinition.builder().routeId("wildcard").pathPattern("/api/v1/**").order(0).build();

        List<RouteDefinition> list = new ArrayList<>(List.of(wildcard, exact));
        list.sort(RouteComparator.INSTANCE);

        assertEquals("exact", list.get(0).getRouteId());
        assertEquals("wildcard", list.get(1).getRouteId());
    }

    @Test
    void shouldPrioritizeLongerLiteralPrefix() {
        RouteDefinition shortPrefix = RouteDefinition.builder().routeId("short").pathPattern("/api/**").order(0).build();
        RouteDefinition longPrefix = RouteDefinition.builder().routeId("long").pathPattern("/api/v1/orders/**").order(0).build();

        List<RouteDefinition> list = new ArrayList<>(List.of(shortPrefix, longPrefix));
        list.sort(RouteComparator.INSTANCE);

        assertEquals("long", list.get(0).getRouteId());
        assertEquals("short", list.get(1).getRouteId());
    }

    @Test
    void shouldPrioritizeSpecificMethodOverAny() {
        RouteDefinition anyMethod = RouteDefinition.builder().routeId("any").pathPattern("/api/v1/users").order(0).build();
        RouteDefinition postMethod = RouteDefinition.builder().routeId("post").pathPattern("/api/v1/users").methods(Set.of("POST")).order(0).build();

        List<RouteDefinition> list = new ArrayList<>(List.of(anyMethod, postMethod));
        list.sort(RouteComparator.INSTANCE);

        assertEquals("post", list.get(0).getRouteId());
        assertEquals("any", list.get(1).getRouteId());
    }
}
