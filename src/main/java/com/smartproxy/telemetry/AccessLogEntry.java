package com.smartproxy.telemetry;

import java.time.Instant;

/**
 * Structured telemetry access log model representing a processed gateway request.
 */
public record AccessLogEntry(
        String traceId,
        String requestId,
        Instant timestamp,
        String method,
        String path,
        String query,
        int statusCode,
        long durationMs,
        String clientIp,
        String clientId,
        String tenantId,
        String serviceId,
        String instanceTarget,
        String routeId,
        int retryCount,
        boolean rateLimited,
        String circuitState,
        String userAgent
) {}
