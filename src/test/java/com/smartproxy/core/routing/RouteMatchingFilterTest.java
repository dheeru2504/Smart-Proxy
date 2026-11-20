package com.smartproxy.core.routing;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RouteMatchingFilterTest {

    @Test
    void shouldMatchRouteAndSetExchangeAttribute() {
        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-route")
                .pathPattern("/api/v1/orders/**")
                .serviceId("order-service")
                .build();

        RouteRegistry registry = new InMemoryRouteRegistry(List.of(route));
        RouteMatchingFilter filter = new RouteMatchingFilter(registry);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            RouteDefinition matched = ex.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);
            assertNotNull(matched);
            assertEquals("orders-route", matched.getRouteId());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertTrue(chainInvoked.get());
    }

    @Test
    void shouldThrowGatewayNotFoundExceptionWhenNoRouteMatches() {
        RouteRegistry registry = new InMemoryRouteRegistry(List.of());
        RouteMatchingFilter filter = new RouteMatchingFilter(registry);

        MockServerHttpRequest request = MockServerHttpRequest.get("/unmatched/path").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(throwable -> throwable instanceof GatewayException ge &&
                        ge.getStatus().value() == 404 &&
                        ge.getErrorCode().equals("ROUTE_NOT_FOUND"))
                .verify();
    }

    @Test
    void shouldBypassInternalEndpoints() {
        RouteRegistry registry = new InMemoryRouteRegistry(List.of());
        RouteMatchingFilter filter = new RouteMatchingFilter(registry);

        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertTrue(chainInvoked.get());
    }
}
