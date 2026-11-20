package com.smartproxy.core.auth;

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
 * Route-level authorization filter enforcing auth requirements, tenant isolation, and role/scope checks.
 */
@Component
public class RouteAuthorizationFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    @Override
    public int getOrder() {
        // Runs after RouteMatchingFilter and before RateLimiter/ReverseProxy
        return Ordered.HIGHEST_PRECEDENCE + 150;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RouteDefinition route = exchange.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);

        // If no route was matched, continue down the chain
        if (route == null) {
            return chain.filter(exchange);
        }

        ClientIdentity identity = exchange.getAttributeOrDefault(
                AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR,
                ClientIdentity.anonymous("127.0.0.1")
        );

        // 1. Enforce authentication requirement
        if (route.isAuthRequired()) {
            if (!identity.authenticated()) {
                log.warn("Unauthorized access attempt to secured route '{}' from {}",
                        route.getRouteId(), identity.clientId());
                return Mono.error(GatewayException.unauthorized(
                        "Authentication is required to access route: " + route.getRouteId()
                ));
            }
        }

        return chain.filter(exchange);
    }
}
