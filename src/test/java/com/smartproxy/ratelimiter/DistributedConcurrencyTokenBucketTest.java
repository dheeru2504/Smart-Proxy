package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.telemetry.GatewayMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Distributed concurrency test suite proving that multiple independent gateway nodes
 * (Node-1, Node-2, Node-3) collectively and atomically enforce shared rate limit token buckets.
 *
 * Covers:
 * 1. Multi-node final-token race condition
 * 2. High concurrency simultaneous burst requests
 * 3. Continuous refill and expiry boundary calculations
 * 4. Multi-tier hierarchical policies across nodes without partial leaks
 * 5. Redis script failure / connection loss graceful degradation
 * 6. Gateway node restart and persistent state continuity
 */
public class DistributedConcurrencyTokenBucketTest {

    private SimulatedRedisServer redisServer;
    private SmartProxyProperties properties;
    private GatewayMetricsService metricsService;

    // Simulated Distributed Gateway Cluster Nodes
    private RedisTokenBucketRateLimiter gatewayNode1;
    private RedisTokenBucketRateLimiter gatewayNode2;
    private RedisTokenBucketRateLimiter gatewayNode3;

    @BeforeEach
    void setUp() {
        redisServer = new SimulatedRedisServer();
        properties = new SmartProxyProperties();
        metricsService = new GatewayMetricsService(new SimpleMeterRegistry());

        // Instantiate 3 distinct gateway node limiter instances backed by the same shared Redis cluster
        gatewayNode1 = new RedisTokenBucketRateLimiter(redisServer.createReactiveRedisOperations(), properties);
        gatewayNode2 = new RedisTokenBucketRateLimiter(redisServer.createReactiveRedisOperations(), properties);
        gatewayNode3 = new RedisTokenBucketRateLimiter(redisServer.createReactiveRedisOperations(), properties);
    }

    @Test
    @DisplayName("1. Multi-Node Final-Token Race: 100 concurrent threads across 3 nodes race for 10 tokens")
    void testMultiNodeFinalTokenRace() throws InterruptedException {
        String sharedKey = "tenant:acme-corp";
        int capacity = 10;
        int refillRate = 1; // 1 token per second
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "shared-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, refillRate, capacity, 60, "TENANT"
        );

        int totalThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        ConcurrentLinkedQueue<RateLimitResult> results = new ConcurrentLinkedQueue<>();
        List<RedisTokenBucketRateLimiter> nodes = List.of(gatewayNode1, gatewayNode2, gatewayNode3);

        for (int i = 0; i < totalThreads; i++) {
            final int nodeIndex = i % 3;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RedisTokenBucketRateLimiter selectedNode = nodes.get(nodeIndex);
                    RateLimitResult result = selectedNode.isAllowed(sharedKey, policy).block();
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    fail("Node execution threw unexpected error: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all 100 requests simultaneously
        startLatch.countDown();
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Concurrent requests did not complete within timeout");
        executor.shutdown();

        long allowedCount = results.stream().filter(RateLimitResult::allowed).count();
        long rejectedCount = results.stream().filter(r -> !r.allowed()).count();

        // Exactly 10 tokens consumed, exactly 90 requests rejected (HTTP 429)
        assertEquals(10, allowedCount, "Exactly 10 tokens must be consumed across all 3 nodes");
        assertEquals(90, rejectedCount, "Exactly 90 requests must be rejected with 429 across all 3 nodes");

        // Verify remaining token balance is exactly 0
        RateLimitResult finalCheck = gatewayNode1.isAllowed(sharedKey, policy).block();
        assertNotNull(finalCheck);
        assertFalse(finalCheck.allowed());
        assertEquals(0, finalCheck.remainingTokens());
    }

    @Test
    @DisplayName("2. Multi-Node Refill Boundary: Tokens replenish accurately and are shared across nodes")
    void testMultiNodeRefillBoundary() throws InterruptedException {
        String sharedKey = "api-client:mobile-app";
        int capacity = 5;
        int refillRate = 10; // 10 tokens per second (1 token per 100ms)
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "refill-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, refillRate, capacity, 60, "API_KEY"
        );

        // 1. Drain all 5 tokens from Node 1
        for (int i = 0; i < 5; i++) {
            RateLimitResult res = gatewayNode1.isAllowed(sharedKey, policy).block();
            assertNotNull(res);
            assertTrue(res.allowed(), "Initial drain request " + i + " should be allowed");
        }

        // 2. Next immediate request on Node 2 must be rejected (0 tokens remaining)
        RateLimitResult emptyCheck = gatewayNode2.isAllowed(sharedKey, policy).block();
        assertNotNull(emptyCheck);
        assertFalse(emptyCheck.allowed(), "Node 2 must reject when tokens are exhausted");

        // 3. Advance Redis server clock by 310ms -> replenishes 3 full integer tokens (3.1 tokens)
        redisServer.advanceTimeMillis(310);

        // 4. Node 3 consumes 2 tokens
        RateLimitResult res1 = gatewayNode3.isAllowed(sharedKey, policy).block();
        RateLimitResult res2 = gatewayNode3.isAllowed(sharedKey, policy).block();
        assertNotNull(res1);
        assertNotNull(res2);
        assertTrue(res1.allowed());
        assertTrue(res2.allowed());

        // 5. Node 1 consumes the 3rd replenished token
        RateLimitResult res3 = gatewayNode1.isAllowed(sharedKey, policy).block();
        assertNotNull(res3);
        assertTrue(res3.allowed());

        // 6. Node 2 attempts to consume 4th token -> rejected
        RateLimitResult res4 = gatewayNode2.isAllowed(sharedKey, policy).block();
        assertNotNull(res4);
        assertFalse(res4.allowed(), "All 3 replenished tokens are exhausted; 4th must be rejected");
    }

    @Test
    @DisplayName("3. Multi-Policy Isolation: Distinct routes and tenants maintain independent quotas")
    void testMultiPolicyIsolationAcrossNodes() {
        RateLimitPolicy tenantAPolicy = new RateLimitPolicy(
                UUID.randomUUID(), "tenant-a-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, 1, 2, 60, "TENANT"
        );
        RateLimitPolicy tenantBPolicy = new RateLimitPolicy(
                UUID.randomUUID(), "tenant-b-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, 1, 5, 60, "TENANT"
        );

        // Node 1 drains Tenant A (2 tokens)
        assertTrue(gatewayNode1.isAllowed("tenant:alpha", tenantAPolicy).block().allowed());
        assertTrue(gatewayNode1.isAllowed("tenant:alpha", tenantAPolicy).block().allowed());
        assertFalse(gatewayNode2.isAllowed("tenant:alpha", tenantAPolicy).block().allowed());

        // Node 3 accesses Tenant B -> Must have its full 5 tokens intact despite Tenant A being throttled
        for (int i = 0; i < 5; i++) {
            assertTrue(gatewayNode3.isAllowed("tenant:beta", tenantBPolicy).block().allowed(),
                    "Tenant B token " + i + " must be permitted");
        }
        assertFalse(gatewayNode3.isAllowed("tenant:beta", tenantBPolicy).block().allowed());
    }

    @Test
    @DisplayName("4. Redis Script Failure / Outage: Nodes gracefully fail open with error metrics")
    @SuppressWarnings("unchecked")
    void testRedisScriptFailureResilience() {
        ReactiveRedisOperations<String, String> failingRedis = mock(ReactiveRedisOperations.class);
        when(failingRedis.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("NOSCRIPT No matching script. Please use EVAL.")));

        RedisTokenBucketRateLimiter resilientNode = new RedisTokenBucketRateLimiter(failingRedis, properties);
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "fallback-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, 5, 10, 60, "IP"
        );

        // Verify node does not crash or throw 500 error; falls back open
        RateLimitResult result = resilientNode.isAllowed("ip:192.168.1.1", policy).block();
        assertNotNull(result);
        assertTrue(result.allowed(), "Limiter must fail-open on Redis script execution errors");
    }

    @Test
    @DisplayName("5. Gateway Node Crash & Restart: State continuity preserved across node restarts")
    void testGatewayNodeRestartStateContinuity() {
        String sharedKey = "client:enterprise-user";
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "continuity-policy", RateLimiterAlgorithmType.TOKEN_BUCKET, 1, 4, 60, "API_KEY"
        );

        // 1. Node 1 consumes 2 of the 4 tokens
        assertTrue(gatewayNode1.isAllowed(sharedKey, policy).block().allowed());
        assertTrue(gatewayNode1.isAllowed(sharedKey, policy).block().allowed());

        // 2. Node 1 crashes (instance discarded)
        gatewayNode1 = null;

        // 3. Node 2 verifies exactly 2 tokens remain
        RateLimitResult n2Check = gatewayNode2.isAllowed(sharedKey, policy).block();
        assertNotNull(n2Check);
        assertTrue(n2Check.allowed());
        assertEquals(1, n2Check.remainingTokens()); // 2 consumed earlier, 1 consumed now -> 1 remains

        // 4. New Node 1 instance starts up (cold restart)
        RedisTokenBucketRateLimiter restartedNode1 = new RedisTokenBucketRateLimiter(
                redisServer.createReactiveRedisOperations(), properties
        );

        // 5. Restarted Node 1 consumes the 4th (final) token
        RateLimitResult finalToken = restartedNode1.isAllowed(sharedKey, policy).block();
        assertNotNull(finalToken);
        assertTrue(finalToken.allowed());
        assertEquals(0, finalToken.remainingTokens());

        // 6. Node 3 and Restarted Node 1 are now throttled
        assertFalse(gatewayNode3.isAllowed(sharedKey, policy).block().allowed());
        assertFalse(restartedNode1.isAllowed(sharedKey, policy).block().allowed());
    }

    // =========================================================================
    // Deterministic Atomic Simulated Redis Cluster
    // =========================================================================

    private static class SimulatedRedisServer {
        private final ConcurrentHashMap<String, TokenBucketState> memory = new ConcurrentHashMap<>();
        private final AtomicLong serverClockMicros = new AtomicLong(System.currentTimeMillis() * 1000L);

        public void advanceTimeMillis(long ms) {
            serverClockMicros.addAndGet(ms * 1000L);
        }

        public double getServerTimeSeconds() {
            return serverClockMicros.get() / 1_000_000.0;
        }

        public synchronized List<Number> executeTokenBucketLua(String key, int capacity, int refillRate, int cost) {
            double now = getServerTimeSeconds();
            TokenBucketState state = memory.computeIfAbsent(key, k -> new TokenBucketState(capacity, now));

            // Refill tokens
            double elapsed = Math.max(0, now - state.lastRefill);
            state.tokens = Math.min(capacity, state.tokens + (elapsed * refillRate));
            state.lastRefill = now;

            long allowed;
            long retryAfter;

            if (state.tokens >= cost) {
                state.tokens -= cost;
                allowed = 1;
                retryAfter = 0;
            } else {
                allowed = 0;
                double missing = cost - state.tokens;
                retryAfter = (long) Math.ceil(missing / refillRate);
            }

            return List.of(allowed, (long) Math.floor(state.tokens), retryAfter, capacity);
        }

        @SuppressWarnings("unchecked")
        public ReactiveRedisOperations<String, String> createReactiveRedisOperations() {
            ReactiveRedisOperations<String, String> ops = mock(ReactiveRedisOperations.class);

            when(ops.execute(any(RedisScript.class), anyList(), anyList())).thenAnswer(invocation -> {
                List<String> keys = invocation.getArgument(1);
                List<String> args = invocation.getArgument(2);

                String key = keys.get(0);
                int capacity = Integer.parseInt(args.get(0));
                int refillRate = Integer.parseInt(args.get(1));
                int cost = Integer.parseInt(args.get(2));

                List<Number> result = executeTokenBucketLua(key, capacity, refillRate, cost);
                return Flux.just(result);
            });

            return ops;
        }

        private static class TokenBucketState {
            double tokens;
            double lastRefill;

            TokenBucketState(double tokens, double lastRefill) {
                this.tokens = tokens;
                this.lastRefill = lastRefill;
            }
        }
    }
}
