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

class RedisFixedWindowRateLimiterTest {

    private ReactiveRedisOperations<String, String> redisOperations;
    private SmartProxyProperties properties;
    private RedisFixedWindowRateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisOperations = (ReactiveRedisOperations<String, String>) mock(ReactiveRedisOperations.class);
        properties = new SmartProxyProperties();
        rateLimiter = new RedisFixedWindowRateLimiter(redisOperations, properties);
    }

    @Test
    void shouldPermitRequestWhenWithinCapacity() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "fixed-policy", RateLimiterAlgorithmType.FIXED_WINDOW,
                10, 10, 60, "IP"
        );

        // Redis Lua returns: {allowed (1), remaining (9), ttl (58), current (1)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 9L, 58L, 1L)));

        StepVerifier.create(rateLimiter.isAllowed("192.168.1.1", policy))
                .assertNext(result -> {
                    assertTrue(result.allowed());
                    assertEquals(10, result.limit());
                    assertEquals(9, result.remainingTokens());
                    assertEquals(58, result.ttlSeconds());
                    assertEquals("192.168.1.1", result.limitKey());
                })
                .verifyComplete();
    }

    @Test
    void shouldThrottleRequestWhenCapacityExceeded() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "fixed-policy", RateLimiterAlgorithmType.FIXED_WINDOW,
                10, 10, 60, "IP"
        );

        // Redis Lua returns: {allowed (0), remaining (0), ttl (42), current (11)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 42L, 11L)));

        StepVerifier.create(rateLimiter.isAllowed("192.168.1.1", policy))
                .assertNext(result -> {
                    assertFalse(result.allowed());
                    assertEquals(10, result.limit());
                    assertEquals(0, result.remainingTokens());
                    assertEquals(42, result.ttlSeconds());
                })
                .verifyComplete();
    }
}
