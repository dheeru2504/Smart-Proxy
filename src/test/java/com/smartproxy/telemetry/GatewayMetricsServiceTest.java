package com.smartproxy.telemetry;

import com.smartproxy.circuitbreaker.CircuitState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private GatewayMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new GatewayMetricsService(meterRegistry);
    }

    @Test
    void shouldRecordHttpRequestRpsAndLatencyPercentiles() {
        metricsService.recordHttpRequest("orders-route", "GET", 200, "tenant-alpha", 45L);
        metricsService.recordHttpRequest("orders-route", "GET", 200, "tenant-alpha", 55L);

        Counter requestCounter = meterRegistry.find("smartproxy.requests.total")
                .tag("route", "orders-route")
                .tag("status", "200")
                .counter();

        assertNotNull(requestCounter);
        assertEquals(2.0, requestCounter.count());

        Timer latencyTimer = meterRegistry.find("smartproxy.requests.latency")
                .tag("route", "orders-route")
                .timer();

        assertNotNull(latencyTimer);
        assertEquals(2, latencyTimer.count());
    }

    @Test
    void shouldRecordRateLimiterRejectionsAndRedisMetrics() {
        metricsService.recordRateLimitRejection("global-policy", "IP", "rl:ip:127.0.0.1");
        metricsService.recordRedisMetric("eval_token_bucket", 1_500_000L, false);
        metricsService.recordRedisMetric("eval_token_bucket", 2_000_000L, true);

        Counter rejectedCounter = meterRegistry.find("smartproxy.ratelimiter.rejected.total").counter();
        assertNotNull(rejectedCounter);
        assertEquals(1.0, rejectedCounter.count());

        Counter redisErrors = meterRegistry.find("smartproxy.ratelimiter.redis.errors").counter();
        assertNotNull(redisErrors);
        assertEquals(1.0, redisErrors.count());
    }

    @Test
    void shouldTrackCircuitBreakerStateGauge() {
        metricsService.recordCircuitState("order-service", CircuitState.CLOSED);
        Gauge closedGauge = meterRegistry.find("smartproxy.circuitbreaker.state").tag("service", "order-service").gauge();
        assertNotNull(closedGauge);
        assertEquals(0.0, closedGauge.value());

        metricsService.recordCircuitState("order-service", CircuitState.OPEN);
        assertEquals(2.0, closedGauge.value());

        metricsService.recordCircuitState("order-service", CircuitState.HALF_OPEN);
        assertEquals(1.0, closedGauge.value());
    }

    @Test
    void shouldTrackHealthCheckGauges() {
        metricsService.recordHealthStatus("payment-service", 5, 1, 0);

        Gauge healthyGauge = meterRegistry.find("smartproxy.healthcheck.instances.healthy").tag("service", "payment-service").gauge();
        assertNotNull(healthyGauge);
        assertEquals(5.0, healthyGauge.value());

        Gauge suspectGauge = meterRegistry.find("smartproxy.healthcheck.instances.suspect").tag("service", "payment-service").gauge();
        assertNotNull(suspectGauge);
        assertEquals(1.0, suspectGauge.value());
    }

    @Test
    void shouldTrackBackendFailuresTimeoutsAndRetries() {
        metricsService.recordBackendCall("inventory-service", "127.0.0.1:8080", "POST", 504, 3000L, true, false);
        metricsService.recordRetry("inventory-service", "GET", 2);

        Counter timeoutCounter = meterRegistry.find("smartproxy.backend.timeouts").counter();
        assertNotNull(timeoutCounter);
        assertEquals(1.0, timeoutCounter.count());

        Counter retryCounter = meterRegistry.find("smartproxy.backend.retries").counter();
        assertNotNull(retryCounter);
        assertEquals(1.0, retryCounter.count());
    }

    @Test
    void shouldTrackConfigPropagationAndVersion() {
        metricsService.recordConfigPropagation(42L, 12L);

        Gauge versionGauge = meterRegistry.find("smartproxy.config.version").gauge();
        assertNotNull(versionGauge);
        assertEquals(42.0, versionGauge.value());

        Gauge lagGauge = meterRegistry.find("smartproxy.config.propagation.lag").gauge();
        assertNotNull(lagGauge);
        assertEquals(12.0, lagGauge.value());
    }
}
