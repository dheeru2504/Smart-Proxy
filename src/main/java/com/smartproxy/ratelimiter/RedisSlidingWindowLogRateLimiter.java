package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Distributed Sliding-Window Log rate limiter using Redis Sorted Sets (ZSET).
 * Delivers 100% boundary precision with atomic ZREMRANGEBYSCORE memory bounding.
 */
@Component
public class RedisSlidingWindowLogRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisSlidingWindowLogRateLimiter.class);

    private static final String SLIDING_WINDOW_LOG_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local unique_id = ARGV[3] or ""

            local time = redis.call('TIME')
            local now_sec = tonumber(time[1])
            local now_micro = tonumber(time[2])
            local now_ms = (now_sec * 1000) + math.floor(now_micro / 1000)
            local now_exact = now_sec + (now_micro / 1000000.0)

            local window_start_ms = now_ms - window_ms

            -- 1. Remove expired entries to bound memory
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start_ms)

            -- 2. Count active entries in sliding window
            local current_count = redis.call('ZCARD', key)

            local allowed = 0
            local remaining = 0
            local retry_after = 0

            if current_count < capacity then
                -- 3. Accept and record entry
                local member = tostring(now_exact) .. '-' .. unique_id
                redis.call('ZADD', key, now_ms, member)
                allowed = 1
                remaining = capacity - current_count - 1
                retry_after = 0
            else
                -- 4. Reject and compute exact retry-after based on oldest entry
                allowed = 0
                remaining = 0
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                if oldest and #oldest >= 2 then
                    local oldest_score = tonumber(oldest[2])
                    local wait_ms = math.max(1, (oldest_score + window_ms) - now_ms)
                    retry_after = math.ceil(wait_ms / 1000.0)
                else
                    retry_after = math.ceil(window_ms / 1000.0)
                end
            end

            -- 5. Set key TTL
            redis.call('PEXPIRE', key, math.max(60000, window_ms * 2))

            return {allowed, remaining, retry_after, capacity}
            """;

    private final ReactiveRedisOperations<String, String> redisOperations;
    private final SmartProxyProperties properties;
    private final RedisScript<List> script;

    @Autowired
    public RedisSlidingWindowLogRateLimiter(@Autowired(required = false) ReactiveRedisOperations<String, String> redisOperations,
                                           SmartProxyProperties properties) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(SLIDING_WINDOW_LOG_LUA, List.class);
    }

    public RedisSlidingWindowLogRateLimiter(ReactiveRedisOperations<String, String> redisOperations,
                                           SmartProxyProperties properties,
                                           RedisScript<List> script) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = script != null ? script : new DefaultRedisScript<>(SLIDING_WINDOW_LOG_LUA, List.class);
    }

    @Override
    public RateLimiterAlgorithmType getAlgorithmType() {
        return RateLimiterAlgorithmType.SLIDING_WINDOW_LOG;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        if (key == null || policy == null) {
            return Mono.just(RateLimitResult.permitted(100, 100, 0, key, getAlgorithmType()));
        }

        int capacity = Math.max(1, policy.burstCapacity());
        int windowMs = Math.max(1000, policy.windowDurationSec() * 1000);
        String redisKeyPrefix = properties.getRatelimiter().getRedisKeyPrefix();
        String fullRedisKey = redisKeyPrefix + "swl:" + key;
        String uniqueEntropy = UUID.randomUUID().toString().substring(0, 8);

        if (redisOperations == null) {
            log.trace("Redis operations not available. Permitting under fallback mode.");
            return Mono.just(RateLimitResult.permitted(capacity, capacity, 0, key, getAlgorithmType()));
        }

        return redisOperations.execute(
                script,
                Collections.singletonList(fullRedisKey),
                List.of(String.valueOf(capacity), String.valueOf(windowMs), uniqueEntropy)
        ).next()
        .map(rawResult -> parseResult(rawResult, capacity, key))
        .onErrorResume(throwable -> {
            log.warn("Redis sliding window log error for key '{}': {}. Applying fallback mode: {}",
                    key, throwable.getMessage(), properties.getRatelimiter().getFallbackMode());
            if ("FAIL_CLOSED".equalsIgnoreCase(properties.getRatelimiter().getFallbackMode())) {
                return Mono.just(RateLimitResult.throttled(capacity, policy.windowDurationSec(), key, getAlgorithmType()));
            }
            // Default: FAIL_OPEN
            return Mono.just(RateLimitResult.permitted(capacity, capacity, 0, key, getAlgorithmType()));
        });
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult parseResult(Object rawResult, int capacity, String key) {
        if (rawResult instanceof List<?> list && list.size() >= 3) {
            long allowed = ((Number) list.get(0)).longValue();
            long remaining = ((Number) list.get(1)).longValue();
            long retryAfter = ((Number) list.get(2)).longValue();

            if (allowed == 1L) {
                return RateLimitResult.permitted(capacity, remaining, retryAfter, key, getAlgorithmType());
            } else {
                return RateLimitResult.throttled(capacity, retryAfter, key, getAlgorithmType());
            }
        }

        return RateLimitResult.permitted(capacity, capacity, 0, key, getAlgorithmType());
    }
}
