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

class RedisTokenBucketRateLimiterTest {

    private ReactiveRedisOperations<String, String> redisOperations;
    private SmartProxyProperties properties;
    private RedisTokenBucketRateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisOperations = (ReactiveRedisOperations<String, String>) mock(ReactiveRedisOperations.class);
        properties = new SmartProxyProperties();
        rateLimiter = new RedisTokenBucketRateLimiter(redisOperations, properties);
    }

    @Test
    void shouldPermitWhenTokensAvailable() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "tb-policy", RateLimiterAlgorithmType.TOKEN_BUCKET,
                10, 100, 60, "USER"
        );

        // Redis Lua returns: {allowed (1), remainingTokens (99), retryAfter (0), capacity (100)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 99L, 0L, 100L)));

        StepVerifier.create(rateLimiter.isAllowed("user-123", policy))
                .assertNext(result -> {
                    assertTrue(result.allowed());
                    assertEquals(100, result.limit());
                    assertEquals(99, result.remainingTokens());
                    assertEquals(0, result.ttlSeconds());
                    assertEquals("user-123", result.limitKey());
                    assertEquals(RateLimiterAlgorithmType.TOKEN_BUCKET, result.algorithm());
                })
                .verifyComplete();
    }

    @Test
    void shouldThrottleWhenBucketDepleted() {
        RateLimitPolicy policy = new RateLimitPolicy(
                UUID.randomUUID(), "tb-policy", RateLimiterAlgorithmType.TOKEN_BUCKET,
                10, 100, 60, "USER"
        );

        // Redis Lua returns: {allowed (0), remainingTokens (0), retryAfter (5), capacity (100)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 5L, 100L)));

        StepVerifier.create(rateLimiter.isAllowed("user-123", policy))
                .assertNext(result -> {
                    assertFalse(result.allowed());
                    assertEquals(100, result.limit());
                    assertEquals(0, result.remainingTokens());
                    assertEquals(5, result.ttlSeconds());
                })
                .verifyComplete();
    }
}
