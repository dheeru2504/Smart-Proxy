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

/**
 * Distributed Fixed-Window rate limiter executing atomic Redis Lua scripts.
 * Eliminates GET-then-INCR race conditions under concurrency.
 */
@Component
public class RedisFixedWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisFixedWindowRateLimiter.class);

    private static final String FIXED_WINDOW_LUA = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                ttl = tonumber(ARGV[2])
            end
            local max = tonumber(ARGV[1])
            if current > max then
                return {0, 0, ttl, current}
            else
                local remaining = max - current
                return {1, remaining, ttl, current}
            end
            """;

    private final ReactiveRedisOperations<String, String> redisOperations;
    private final SmartProxyProperties properties;
    private final RedisScript<List> script;

    @Autowired
    public RedisFixedWindowRateLimiter(@Autowired(required = false) ReactiveRedisOperations<String, String> redisOperations,
                                       SmartProxyProperties properties) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(FIXED_WINDOW_LUA, List.class);
    }

    public RedisFixedWindowRateLimiter(ReactiveRedisOperations<String, String> redisOperations,
                                       SmartProxyProperties properties,
                                       RedisScript<List> script) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = script != null ? script : new DefaultRedisScript<>(FIXED_WINDOW_LUA, List.class);
    }

    @Override
    public RateLimiterAlgorithmType getAlgorithmType() {
        return RateLimiterAlgorithmType.FIXED_WINDOW;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        if (key == null || policy == null) {
            return Mono.just(RateLimitResult.permitted(100, 100, 0, key, RateLimiterAlgorithmType.FIXED_WINDOW));
        }

        int capacity = Math.max(1, policy.burstCapacity());
        int windowDurationSec = Math.max(1, policy.windowDurationSec());
        String redisKeyPrefix = properties.getRatelimiter().getRedisKeyPrefix();
        String fullRedisKey = redisKeyPrefix + "fw:" + key;

        if (redisOperations == null) {
            log.trace("Redis operations not available. Permitting under fallback mode.");
            return Mono.just(RateLimitResult.permitted(capacity, capacity, windowDurationSec, key, getAlgorithmType()));
        }

        return redisOperations.execute(
                script,
                Collections.singletonList(fullRedisKey),
                List.of(String.valueOf(capacity), String.valueOf(windowDurationSec))
        ).next()
        .map(rawResult -> parseResult(rawResult, capacity, windowDurationSec, key))
        .onErrorResume(throwable -> {
            log.warn("Redis rate limiter error for key '{}': {}. Applying fallback mode: {}",
                    key, throwable.getMessage(), properties.getRatelimiter().getFallbackMode());
            if ("FAIL_CLOSED".equalsIgnoreCase(properties.getRatelimiter().getFallbackMode())) {
                return Mono.just(RateLimitResult.throttled(capacity, windowDurationSec, key, getAlgorithmType()));
            }
            // Default: FAIL_OPEN
            return Mono.just(RateLimitResult.permitted(capacity, capacity, windowDurationSec, key, getAlgorithmType()));
        });
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult parseResult(Object rawResult, int capacity, int windowSec, String key) {
        if (rawResult instanceof List<?> list && list.size() >= 3) {
            long allowed = ((Number) list.get(0)).longValue();
            long remaining = ((Number) list.get(1)).longValue();
            long ttl = ((Number) list.get(2)).longValue();

            if (allowed == 1L) {
                return RateLimitResult.permitted(capacity, remaining, ttl, key, getAlgorithmType());
            } else {
                return RateLimitResult.throttled(capacity, ttl, key, getAlgorithmType());
            }
        }

        return RateLimitResult.permitted(capacity, capacity, windowSec, key, getAlgorithmType());
    }
}
