package com.smartproxy.telemetry;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable telemetry event representing an intercepted and proxied request.
 */
public record GatewayAccessEvent(
        String traceId,
        Instant timestamp,
        String clientIp,
        String httpMethod,
        String requestPath,
        int statusCode,
        long durationMs,
        String routeId,
        String upstreamInstance,
        boolean rateLimitExceeded,
        long rateLimitRemaining,
        boolean circuitBreakerTriggered,
        String errorMessage,
        Map<String, String> customHeaders
) implements Serializable {}
