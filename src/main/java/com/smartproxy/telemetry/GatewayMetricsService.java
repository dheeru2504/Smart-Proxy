package com.smartproxy.telemetry;

import com.smartproxy.circuitbreaker.CircuitState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central gateway metrics engine managing Prometheus / Micrometer meters across
 * RPS, latency percentiles, 429s, Redis metrics, backend reliability, retries,
 * circuit breakers, health checks, load balancer distribution, and configuration propagation.
 */
@Service
public class GatewayMetricsService {

    private final MeterRegistry registry;

    // Active Gauges
    private final Map<String, AtomicInteger> circuitStates = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> healthyInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> suspectInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> downInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();
    private final AtomicLong activeConfigVersion = new AtomicLong(0);
    private final AtomicLong configPropagationLagMs = new AtomicLong(0);

    public GatewayMetricsService(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();
        registerConfigGauges();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    private void registerConfigGauges() {
        Gauge.builder("smartproxy.config.version", activeConfigVersion, AtomicLong::get)
                .description("Active configuration snapshot version")
                .register(registry);

        Gauge.builder("smartproxy.config.propagation.lag", configPropagationLagMs, AtomicLong::get)
                .description("Configuration propagation lag in milliseconds")
                .baseUnit("milliseconds")
                .register(registry);
    }

    /**
     * Records an incoming HTTP request with duration, status, and percentiles.
     */
    public void recordHttpRequest(String routeId, String method, int statusCode, String tenantId, long durationMs) {
        String routeTag = routeId != null ? routeId : "unmatched";
        String methodTag = method != null ? method : "UNKNOWN";
        String statusTag = String.valueOf(statusCode);
        String tenantTag = sanitizeTagValue(tenantId, "anonymous");

        // 1. Requests Total Counter (RPS)
        Counter.builder("smartproxy.requests.total")
                .tag("route", routeTag)
                .tag("method", methodTag)
                .tag("status", statusTag)
                .tag("tenant", tenantTag)
                .description("Total number of processed HTTP requests")
                .register(registry)
                .increment();

        // 2. Latency Percentiles Timer (p50, p90, p95, p99)
        Timer.builder("smartproxy.requests.latency")
                .tag("route", routeTag)
                .tag("method", methodTag)
                .tag("status", statusTag)
                .publishPercentiles(0.50, 0.90, 0.95, 0.99)
                .description("HTTP request execution latency")
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, durationMs)));
    }

    /**
     * Records a 429 Rate Limit Rejection.
     */
    public void recordRateLimitRejection(String policyName, String dimension, String limitKey) {
        Counter.builder("smartproxy.ratelimiter.rejected.total")
                .tag("policy", policyName != null ? policyName : "default")
                .tag("dimension", dimension != null ? dimension : "IP")
                .description("Total number of rejected 429 requests")
                .register(registry)
                .increment();
    }

    /**
     * Records Redis operation latency and errors.
     */
    public void recordRedisMetric(String operation, long durationNanos, boolean isError) {
        Timer.builder("smartproxy.ratelimiter.redis.latency")
                .tag("operation", operation != null ? operation : "eval")
                .description("Redis Lua script execution duration")
                .register(registry)
                .record(Duration.ofNanos(Math.max(0, durationNanos)));

        if (isError) {
            Counter.builder("smartproxy.ratelimiter.redis.errors")
                    .tag("operation", operation != null ? operation : "eval")
                    .description("Redis operation failures")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Records backend upstream execution outcomes, failures, and timeouts.
     */
    public void recordBackendCall(String serviceId, String instance, String method, int statusCode,
                                  long durationMs, boolean isTimeout, boolean isRefused) {
        String serviceTag = serviceId != null ? serviceId : "unknown";
        String instTag = instance != null ? instance : "unknown";

        Counter.builder("smartproxy.backend.requests")
                .tag("service", serviceTag)
                .tag("instance", instTag)
                .tag("status", String.valueOf(statusCode))
                .description("Total backend upstream requests")
                .register(registry)
                .increment();

        if (isTimeout) {
            Counter.builder("smartproxy.backend.timeouts")
                    .tag("service", serviceTag)
                    .description("Backend upstream timeouts")
                    .register(registry)
                    .increment();
        }

        if (isRefused || statusCode >= 500) {
            Counter.builder("smartproxy.backend.failures")
                    .tag("service", serviceTag)
                    .tag("status", String.valueOf(statusCode))
                    .description("Backend upstream connection failures and 5xx errors")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Records retry attempt.
     */
    public void recordRetry(String serviceId, String method, int attempt) {
        Counter.builder("smartproxy.backend.retries")
                .tag("service", serviceId != null ? serviceId : "unknown")
                .tag("method", method != null ? method : "UNKNOWN")
                .tag("attempt", String.valueOf(attempt))
                .description("Total outbound request retries")
                .register(registry)
                .increment();
    }

    /**
     * Updates circuit breaker state gauge (0 = CLOSED, 1 = HALF_OPEN, 2 = OPEN).
     */
    public void recordCircuitState(String serviceId, CircuitState state) {
        if (serviceId == null || state == null) return;

        int value = switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };

        AtomicInteger gaugeHolder = circuitStates.computeIfAbsent(serviceId, s -> {
            AtomicInteger ai = new AtomicInteger(0);
            Gauge.builder("smartproxy.circuitbreaker.state", ai, AtomicInteger::get)
                    .tag("service", s)
                    .description("Circuit breaker state: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                    .register(registry);
            return ai;
        });

        gaugeHolder.set(value);
    }

    /**
     * Updates active health check status counts.
     */
    public void recordHealthStatus(String serviceId, int healthy, int suspect, int down) {
        if (serviceId == null) return;

        healthyInstances.computeIfAbsent(serviceId, s -> {
            AtomicInteger ai = new AtomicInteger(0);
            Gauge.builder("smartproxy.healthcheck.instances.healthy", ai, AtomicInteger::get)
                    .tag("service", s).register(registry);
            return ai;
        }).set(healthy);

        suspectInstances.computeIfAbsent(serviceId, s -> {
            AtomicInteger ai = new AtomicInteger(0);
            Gauge.builder("smartproxy.healthcheck.instances.suspect", ai, AtomicInteger::get)
                    .tag("service", s).register(registry);
            return ai;
        }).set(suspect);

        downInstances.computeIfAbsent(serviceId, s -> {
            AtomicInteger ai = new AtomicInteger(0);
            Gauge.builder("smartproxy.healthcheck.instances.down", ai, AtomicInteger::get)
                    .tag("service", s).register(registry);
            return ai;
        }).set(down);
    }

    /**
     * Records load balancer routing selection.
     */
    public void recordLoadBalancerSelection(String serviceId, String instance, String strategy) {
        Counter.builder("smartproxy.loadbalancer.selections")
                .tag("service", serviceId != null ? serviceId : "unknown")
                .tag("instance", instance != null ? instance : "unknown")
                .tag("strategy", strategy != null ? strategy : "unknown")
                .description("Load balancer instance selection distribution")
                .register(registry)
                .increment();
    }

    /**
     * Updates active configuration version and propagation lag.
     */
    public void recordConfigPropagation(long version, long propagationLagMs) {
        this.activeConfigVersion.set(version);
        this.configPropagationLagMs.set(propagationLagMs);
    }

    private static String sanitizeTagValue(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }
        return trimmed.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
