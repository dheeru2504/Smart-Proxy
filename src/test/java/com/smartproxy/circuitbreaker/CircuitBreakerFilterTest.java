package com.smartproxy.circuitbreaker;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerFilterTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerFilter filter;

    @BeforeEach
    void setUp() {
        registry = new CircuitBreakerRegistry();
        filter = new CircuitBreakerFilter(registry);
    }

    @Test
    void shouldAllowRequestWhenCircuitClosed() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-route")
                .serviceId("order-service")
                .pathPattern("/api/v1/orders/**")
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldFastFailWhenCircuitOpen() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-route")
                .serviceId("order-service")
                .pathPattern("/api/v1/orders/**")
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);

        // Force circuit breaker to OPEN
        CircuitBreaker cb = registry.getOrCreate("order-service");
        cb.transitionTo(CircuitState.OPEN);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();
    }
}
