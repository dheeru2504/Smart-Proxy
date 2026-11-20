package com.smartproxy.core.foundation;

import com.smartproxy.core.auth.AuthenticationFilter;
import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import com.smartproxy.telemetry.AccessLogEntry;
import com.smartproxy.telemetry.GatewayMetricsService;
import com.smartproxy.telemetry.StructuredAccessLogger;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Non-blocking reactive access logging filter.
 * Emits structured JSON access logs with trace correlation and updates central
 * Micrometer metrics.
 */
@Component
public class AccessLoggingFilter implements WebFilter, Ordered {

    private final StructuredAccessLogger accessLogger;
    private final GatewayMetricsService metricsService;

    public AccessLoggingFilter(StructuredAccessLogger accessLogger, GatewayMetricsService metricsService) {
        this.accessLogger = accessLogger;
        this.metricsService = metricsService;
    }

    @Override
    public int getOrder() {
        // Runs immediately after TraceIdFilter
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTimeNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String traceId = exchange.getAttributeOrDefault(TraceIdFilter.TRACE_ID_ATTR, "UNKNOWN");
        String clientIp = resolveClientIp(request);
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String userAgent = request.getHeaders().getFirst("User-Agent");

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> logAndRecordMetrics(exchange, traceId, clientIp, method, path, query, userAgent,
                        startTimeNanos, null))
                .doOnError(throwable -> logAndRecordMetrics(exchange, traceId, clientIp, method, path, query, userAgent,
                        startTimeNanos, throwable));
    }

    private void logAndRecordMetrics(ServerWebExchange exchange, String traceId, String clientIp,
            String method, String path, String query, String userAgent,
            long startTimeNanos, Throwable error) {
        long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
        ServerHttpResponse response = exchange.getResponse();
        HttpStatusCode statusCode = response.getStatusCode();
        int status = statusCode != null ? statusCode.value() : (error != null ? 500 : 200);

        RouteDefinition route = exchange.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);
        String routeId = route != null ? route.getRouteId() : "unmatched";
        String serviceId = route != null ? route.getServiceId() : "unknown";

        ClientIdentity identity = exchange.getAttribute(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR);
        String clientId = identity != null ? identity.clientId() : "anonymous";
        String tenantId = identity != null ? identity.tenantId() : "anonymous";

        boolean isRateLimited = status == 429;

        // 1. Record in central Micrometer metrics engine
        if (metricsService != null) {
            metricsService.recordHttpRequest(routeId, method, status, tenantId, durationMs);
        }

        // 2. Emit structured access log
        AccessLogEntry entry = new AccessLogEntry(
                traceId,
                traceId,
                Instant.now(),
                method,
                path,
                query,
                status,
                durationMs,
                clientIp,
                clientId,
                tenantId,
                serviceId,
                null,
                routeId,
                0,
                isRateLimited,
                "CLOSED",
                userAgent);

        if (accessLogger != null) {
            accessLogger.logAccess(entry);
        }
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "0.0.0.0";
    }
}
