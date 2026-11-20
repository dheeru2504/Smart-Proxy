package com.smartproxy.core.routing;

import com.smartproxy.core.foundation.TraceIdFilter;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * Reactive filter executing route matching against the immutable RouteRegistry snapshot.
 */
@Component
public class RouteMatchingFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteMatchingFilter.class);

    public static final String MATCHED_ROUTE_ATTR = "com.smartproxy.matchedRoute";

    private static final Set<String> INTERNAL_PATHS = Set.of(
            "/health",
            "/healthz",
            "/readyz"
    );

    private final RouteRegistry routeRegistry;

    public RouteMatchingFilter(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @Override
    public int getOrder() {
        // Runs after TraceIdFilter and AccessLoggingFilter, before business logic
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String traceId = exchange.getAttributeOrDefault(TraceIdFilter.TRACE_ID_ATTR, "UNKNOWN");

        // Bypass internal health and actuator endpoints
        if (isInternalPath(path)) {
            return chain.filter(exchange);
        }

        Optional<RouteDefinition> matchedRoute = routeRegistry.match(method, path);

        if (matchedRoute.isEmpty()) {
            log.warn("[{}] No route matched for {} {}", traceId, method, path);
            return Mono.error(GatewayException.notFound("No active gateway route matched for path: " + path));
        }

        RouteDefinition route = matchedRoute.get();
        log.debug("[{}] Matched route '{}' (Service: '{}', Pattern: '{}') for {} {}",
                traceId, route.getRouteId(), route.getServiceId(), route.getPathPattern(), method, path);

        exchange.getAttributes().put(MATCHED_ROUTE_ATTR, route);

        return chain.filter(exchange);
    }

    private boolean isInternalPath(String path) {
        if (INTERNAL_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("/actuator") || path.startsWith("/api/admin");
    }
}
