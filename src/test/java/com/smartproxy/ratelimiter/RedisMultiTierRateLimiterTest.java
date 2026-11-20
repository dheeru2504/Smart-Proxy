package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RedisMultiTierRateLimiterTest {

    private ReactiveRedisOperations<String, String> redisOperations;
    private SmartProxyProperties properties;
    private RedisMultiTierRateLimiter multiTierRateLimiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisOperations = (ReactiveRedisOperations<String, String>) mock(ReactiveRedisOperations.class);
        properties = new SmartProxyProperties();
        multiTierRateLimiter = new RedisMultiTierRateLimiter(redisOperations, properties);
    }

    @Test
    void shouldPermitWhenAllTiersHaveCapacity() {
        List<RedisMultiTierRateLimiter.TierRequest> tiers = List.of(
                new RedisMultiTierRateLimiter.TierRequest("global", 10000, 10000, 1),
                new RedisMultiTierRateLimiter.TierRequest("tenant:tenant-1", 1000, 500, 1),
                new RedisMultiTierRateLimiter.TierRequest("client:client-a", 200, 100, 1),
                new RedisMultiTierRateLimiter.TierRequest("route:checkout", 50, 25, 1)
        );

        // Redis Lua returns: {allowed (1), min_remaining (49), retryAfter (0), min_capacity (50), failing_key (0)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 49L, 0L, 50L, 0L)));

        StepVerifier.create(multiTierRateLimiter.isAllowed(tiers))
                .assertNext(result -> {
                    assertTrue(result.allowed());
                    assertEquals(50, result.limit());
                    assertEquals(49, result.remainingTokens());
                    assertEquals(0, result.ttlSeconds());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenSingleTierFailsWithoutPartialConsumption() {
        List<RedisMultiTierRateLimiter.TierRequest> tiers = List.of(
                new RedisMultiTierRateLimiter.TierRequest("global", 10000, 10000, 1),
                new RedisMultiTierRateLimiter.TierRequest("tenant:tenant-1", 1000, 500, 1),
                new RedisMultiTierRateLimiter.TierRequest("client:client-a", 200, 100, 1),
                new RedisMultiTierRateLimiter.TierRequest("route:checkout", 50, 25, 1)
        );

        // Redis Lua returns: {allowed (0), min_remaining (0), retryAfter (4), min_capacity (50), failing_key (4)}
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 4L, 50L, 4L)));

        StepVerifier.create(multiTierRateLimiter.isAllowed(tiers))
                .assertNext(result -> {
                    assertFalse(result.allowed());
                    assertEquals(50, result.limit());
                    assertEquals(0, result.remainingTokens());
                    assertEquals(4, result.ttlSeconds());
                })
                .verifyComplete();
    }
}
