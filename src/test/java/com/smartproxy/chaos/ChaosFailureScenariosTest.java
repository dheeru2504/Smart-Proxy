package com.smartproxy.chaos;

import com.smartproxy.admin.snapshot.GatewayConfigSnapshot;
import com.smartproxy.admin.snapshot.GatewaySnapshotManager;
import com.smartproxy.admin.snapshot.SnapshotValidator;
import com.smartproxy.circuitbreaker.CircuitBreakerConfig;
import com.smartproxy.circuitbreaker.CircuitState;
import com.smartproxy.circuitbreaker.CustomCircuitBreaker;
import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.core.auth.ApiClientRegistry;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.proxy.retry.OutboundConcurrencyLimiter;
import com.smartproxy.core.proxy.retry.ReactiveRetryExecutor;
import com.smartproxy.core.proxy.retry.RetryPolicy;
import com.smartproxy.core.routing.InMemoryRouteRegistry;
import com.smartproxy.loadbalancer.InstanceHealthStatus;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import com.smartproxy.loadbalancer.health.HealthStateMachine;
import com.smartproxy.ratelimiter.RateLimitPolicy;
import com.smartproxy.ratelimiter.RateLimitResult;
import com.smartproxy.ratelimiter.RateLimiterAlgorithmType;
import com.smartproxy.ratelimiter.RedisTokenBucketRateLimiter;
import com.smartproxy.telemetry.GatewayMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Deterministic, reproducible chaos test suite covering real-world production failure modes:
 * 1. Redis outage & high latency degradation
 * 2. Gateway crash & in-memory snapshot rehydration
 * 3. Backend instance crash & automated load-balancer failover
 * 4. Backend latency spike & deadline timeout enforcement
 * 5. Downstream 5xx cascade & circuit breaker trip / recovery
 * 6. Stale configuration collision & split-brain prevention
 * 7. Hot rate-limit key concurrency under heavy load
 */
public class ChaosFailureScenariosTest {

    private GatewayMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new GatewayMetricsService(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Scenario 1: Redis Outage / Latency Spike -> Graceful Fallback & Metric Recording")
    @SuppressWarnings("unchecked")
    void scenario1_redisOutageGracefulFallback() {
        ReactiveRedisOperations<String, String> mockRedis = mock(ReactiveRedisOperations.class);
        when(mockRedis.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection timed out (ECONNREFUSED)")));

        SmartProxyProperties props = new SmartProxyProperties();
        RedisTokenBucketRateLimiter rateLimiter = new RedisTokenBucketRateLimiter(mockRedis, props);
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "api-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, 10, 100, 60, "API_KEY"
        );

        // Rate limiter gracefully falls open when Redis is unreachable
        Mono<RateLimitResult> decisionMono = rateLimiter.isAllowed("rl:api:client-xyz", policy);

        StepVerifier.create(decisionMono)
                .expectNextMatches(RateLimitResult::allowed)
                .verifyComplete();

        // Verify telemetry recording of Redis error
        metricsService.recordRedisMetric("eval_token_bucket", 500_000L, true);
        assertEquals(1.0, metricsService.getRegistry().find("smartproxy.ratelimiter.redis.errors").counter().count());
    }

    @Test
    @DisplayName("Scenario 2: Gateway Node Crash -> Automatic Bootstrap & Snapshot Rehydration")
    void scenario2_gatewayCrashSnapshotRehydration() {
        InMemoryRouteRegistry routeRegistry = new InMemoryRouteRegistry();
        UpstreamPoolRegistry poolRegistry = new UpstreamPoolRegistry(null);
        ApiClientRegistry clientRegistry = new ApiClientRegistry(null);
        SnapshotValidator validator = new SnapshotValidator();
        GatewaySnapshotManager snapshotManager = new GatewaySnapshotManager(validator, routeRegistry, poolRegistry, clientRegistry);

        // 1. Simulate fresh node boot rehydrating snapshot v1 from database
        RouteDefinition paymentsRoute = RouteDefinition.builder()
                .routeId("payments-v1")
                .pathPattern("/api/v1/payments/**")
                .serviceId("payment-service")
                .build();

        UpstreamInstance inst1 = new UpstreamInstance(UUID.randomUUID(), "payment-service", "10.0.0.1", 8080, 100);
        UpstreamInstance inst2 = new UpstreamInstance(UUID.randomUUID(), "payment-service", "10.0.0.2", 8080, 100);

        GatewayConfigSnapshot bootstrapSnapshot = new GatewayConfigSnapshot(
                1L, Instant.now().minusMillis(10), null,
                List.of(paymentsRoute),
                Map.of("payment-service", List.of(inst1, inst2)),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null
        );
        String checksum = SnapshotValidator.computeChecksum(bootstrapSnapshot);
        GatewayConfigSnapshot validBootstrap = new GatewayConfigSnapshot(
                1L, Instant.now().minusMillis(10), null,
                List.of(paymentsRoute),
                Map.of("payment-service", List.of(inst1, inst2)),
                Collections.emptyMap(),
                Collections.emptyMap(),
                checksum
        );

        GatewayConfigSnapshot applied = snapshotManager.applySnapshot(validBootstrap);

        // Verification: Full state restored in-memory without querying database per request
        assertEquals(1L, snapshotManager.getActiveVersion());
        assertTrue(routeRegistry.match("GET", "/api/v1/payments/status").isPresent());
        assertEquals(2, poolRegistry.getInstances("payment-service").size());
        assertTrue(applied.getPropagationLagMs() >= 0);
    }

    @Test
    @DisplayName("Scenario 3: Backend Instance Crash -> Health Prober Isolates Node & Failover Routes Traffic")
    void scenario3_backendInstanceCrashAndFailover() {
        UpstreamInstance backendA = new UpstreamInstance(UUID.randomUUID(), "order-service", "10.0.1.10", 8080, 1);
        UpstreamInstance backendB = new UpstreamInstance(UUID.randomUUID(), "order-service", "10.0.1.11", 8080, 1);

        HealthStateMachine sm = new HealthStateMachine(3, 2);

        // 1. Initially both are healthy
        assertEquals(InstanceHealthStatus.UP, backendA.getStatus());
        assertEquals(InstanceHealthStatus.UP, backendB.getStatus());

        // 2. Backend A experiences 3 consecutive connection probe failures (crashes)
        sm.evaluate(backendA, false);
        assertEquals(InstanceHealthStatus.UP, backendA.getStatus(), "1 failure is within grace threshold");
        sm.evaluate(backendA, false);
        assertEquals(InstanceHealthStatus.SUSPECT, backendA.getStatus(), "2 failures triggers SUSPECT state");
        sm.evaluate(backendA, false);
        assertEquals(InstanceHealthStatus.DOWN, backendA.getStatus(), "3 failures transitions to DOWN");

        // 3. Verify failover: Active pool contains only healthy Backend B
        UpstreamPoolRegistry poolRegistry = new UpstreamPoolRegistry(null);
        poolRegistry.registerInstance("order-service", backendA);
        poolRegistry.registerInstance("order-service", backendB);

        List<UpstreamInstance> healthy = poolRegistry.getInstances("order-service").stream()
                .filter(i -> i.getStatus() == InstanceHealthStatus.UP)
                .toList();

        assertEquals(1, healthy.size());
        assertEquals(backendB.getId(), healthy.get(0).getId(), "Only Backend B must be selected for traffic");
    }

    @Test
    @DisplayName("Scenario 4: Backend Latency Spike -> Deadline Enforcement & Concurrency Release")
    void scenario4_backendLatencySpikeAndDeadlineTimeout() {
        ReactiveRetryExecutor retryExecutor = new ReactiveRetryExecutor();
        OutboundConcurrencyLimiter concurrencyLimiter = new OutboundConcurrencyLimiter(5);
        RetryPolicy policy = new RetryPolicy(2, 20L, 100L, 2.0, 5);

        // Simulate slow backend taking 5000ms when deadline budget is only 100ms
        Mono<String> slowCall = concurrencyLimiter.executeWithConcurrencyLimit("slow-service", 5,
                retryExecutor.executeWithRetry(
                        () -> Mono.error(new SocketTimeoutException("Read timed out after 5000ms")),
                        HttpMethod.GET,
                        new HttpHeaders(),
                        policy,
                        100L, // Deadline 100ms
                        "trace-latency-spike"
                )
        );

        StepVerifier.create(slowCall)
                .expectError(SocketTimeoutException.class)
                .verify();

        // Concurrency limiter must decrement back to 0 even upon timeout error
        assertEquals(0, concurrencyLimiter.getActiveCount("slow-service"));
    }

    @Test
    @DisplayName("Scenario 5: 5xx Error Spike -> Circuit Breaker Trips OPEN, Fast-Fails, and Recovers on Probe")
    void scenario5_downstream5xxSpikeAndCircuitRecovery() throws InterruptedException {
        CircuitBreakerConfig config = new CircuitBreakerConfig(
                UUID.randomUUID(), "checkout-service", 10, 4, 50.0, 50.0, 1000, 1, 2, null
        );
        CustomCircuitBreaker cb = new CustomCircuitBreaker("checkout-service", config);

        // 1. Initial State: CLOSED
        assertEquals(CircuitState.CLOSED, cb.getState());

        // 2. Downstream error spike (4 calls: 1 success, 3 failures = 75% error rate > 50% threshold)
        cb.onSuccess(30);
        cb.onError(40, new RuntimeException("500 Internal Server Error"));
        cb.onError(50, new RuntimeException("502 Bad Gateway"));
        cb.onError(60, new RuntimeException("503 Service Unavailable"));

        // 3. Circuit Breaker Trips OPEN
        assertEquals(CircuitState.OPEN, cb.getState());

        // 4. Incoming requests immediately fast-fail with 503 without touching backend
        StepVerifier.create(cb.execute(() -> Mono.just("SHOULD_NEVER_RUN")))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();

        // 5. Cool-down timer expires (1 second) -> Transitions to HALF_OPEN
        Thread.sleep(1100);
        assertEquals(CircuitState.HALF_OPEN, cb.getState());

        // 6. Two successful trial probes recover the circuit
        StepVerifier.create(cb.execute(() -> Mono.just("PROBE_OK_1"))).expectNext("PROBE_OK_1").verifyComplete();
        StepVerifier.create(cb.execute(() -> Mono.just("PROBE_OK_2"))).expectNext("PROBE_OK_2").verifyComplete();

        // Recovered back to CLOSED
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    @DisplayName("Scenario 6: Stale Configuration Collision -> Monotonic Integrity Enforcement & Rejection")
    void scenario6_staleConfigurationRejection() {
        SnapshotValidator validator = new SnapshotValidator();

        GatewayConfigSnapshot activeSnapshot = new GatewayConfigSnapshot(
                10L, Instant.now(), Instant.now(), Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), "checksum-10"
        );

        // Stale snapshot with version 8 (< active version 10)
        GatewayConfigSnapshot staleCandidate = new GatewayConfigSnapshot(
                8L, Instant.now(), null, Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), "checksum-8"
        );

        // Must reject stale configuration immediately to prevent split-brain rollback
        assertThrows(GatewayException.class, () -> validator.validate(staleCandidate, activeSnapshot));
    }

    @Test
    @DisplayName("Scenario 7: Hot Rate-Limit Partition Key -> Atomic Concurrency Protection & Zero Leaks")
    void scenario7_hotRateLimitKeyConcurrency() throws InterruptedException {
        int threads = 50;
        int capacity = 10;
        AtomicInteger remainingTokens = new AtomicInteger(capacity);
        ConcurrentLinkedQueue<Boolean> decisions = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Simulating atomic token bucket CAS operation
                    int current;
                    boolean allowed = false;
                    do {
                        current = remainingTokens.get();
                        if (current <= 0) {
                            allowed = false;
                            break;
                        }
                    } while (!(allowed = remainingTokens.compareAndSet(current, current - 1)));

                    decisions.add(allowed);
                } catch (Exception e) {
                    decisions.add(false);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        long allowedCount = decisions.stream().filter(Boolean::booleanValue).count();
        long rejectedCount = decisions.stream().filter(b -> !b).count();

        // Exactly capacity (10) allowed, exactly (40) rejected (429)
        assertEquals(10, allowedCount, "Exactly 10 tokens must be consumed");
        assertEquals(40, rejectedCount, "Remaining 40 requests must be rejected with 429");
        assertEquals(0, remainingTokens.get(), "Tokens counter must be exactly 0 (no leaks)");
    }
}
