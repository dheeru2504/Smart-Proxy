package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RedisSlidingWindowLogRateLimiterTest {

    private ReactiveRedisOperations<String, String> redisOperations;
    private SmartProxyProperties properties;
    private RedisSlidingWindowLogRateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisOperations = (ReactiveRedisOperations<String, String>) mock(ReactiveRedisOperations.class);
        properties = new SmartProxyProperties();
        rateLimiter = new RedisSlidingWindowLogRateLimiter(redisOperations, properties);
    }

    @Test
    void shouldPermitWhenUnderCapacity() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "swl-policy", RateLimiterAlgorithmType.SLIDING_WINDOW_LOG,
                10, 5, 10, "IP"
        );

        // Redis Lua returns: {allowed (1), remaining (4), retryAfter (0), capacity (5)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 4L, 0L, 5L)));

        StepVerifier.create(rateLimiter.isAllowed("10.0.0.1", policy))
                .assertNext(result -> {
                    assertTrue(result.allowed());
                    assertEquals(5, result.limit());
                    assertEquals(4, result.remainingTokens());
                    assertEquals(0, result.ttlSeconds());
                    assertEquals("10.0.0.1", result.limitKey());
                    assertEquals(RateLimiterAlgorithmType.SLIDING_WINDOW_LOG, result.algorithm());
                })
                .verifyComplete();
    }

    @Test
    void shouldThrottleWhenCapacityExhausted() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "swl-policy", RateLimiterAlgorithmType.SLIDING_WINDOW_LOG,
                10, 5, 10, "IP"
        );

        // Redis Lua returns: {allowed (0), remaining (0), retryAfter (4), capacity (5)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 4L, 5L)));

        StepVerifier.create(rateLimiter.isAllowed("10.0.0.1", policy))
                .assertNext(result -> {
                    assertFalse(result.allowed());
                    assertEquals(5, result.limit());
                    assertEquals(0, result.remainingTokens());
                    assertEquals(4, result.ttlSeconds());
                })
                .verifyComplete();
    }
}
