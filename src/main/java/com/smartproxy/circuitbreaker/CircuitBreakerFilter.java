package com.smartproxy.circuitbreaker;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gateway WebFilter integrating circuit breakers on upstream routes.
 * Fast-fails traffic immediately when the target service circuit breaker is OPEN.
 */
@Component
public class CircuitBreakerFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerFilter(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        // Runs after RateLimitingFilter (HIGHEST_PRECEDENCE + 300) and before ReverseProxyFilter (LOWEST_PRECEDENCE - 100)
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RouteDefinition route = exchange.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);
        if (route == null || route.getServiceId() == null) {
            return chain.filter(exchange);
        }

        String serviceId = route.getServiceId();
        CircuitBreaker cb = registry.getOrCreate(serviceId);

        CircuitState state = cb.getState();
        if (state == CircuitState.OPEN) {
            log.warn("Fast-failing request for service '{}': Circuit breaker is OPEN", serviceId);
            return Mono.error(GatewayException.serviceUnavailable(
                    "Service '" + serviceId + "' is currently unavailable (Circuit Breaker OPEN)."
            ));
        }

        return cb.execute(() -> chain.filter(exchange));
    }
}
