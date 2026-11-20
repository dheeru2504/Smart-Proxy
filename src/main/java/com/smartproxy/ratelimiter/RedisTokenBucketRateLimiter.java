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
 * Primary Distributed Token Bucket rate limiter executing atomic Redis Lua script.
 * Authoritative state, time, refill, decision, and TTL are 100% computed inside Redis.
 */
@Component
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local cost = tonumber(ARGV[3]) or 1

            local time = redis.call('TIME')
            local now = tonumber(time[1]) + (tonumber(time[2]) / 1000000.0)

            local state = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(state[1])
            local last_refill = tonumber(state[2])

            if tokens == nil or last_refill == nil then
                tokens = capacity
                last_refill = now
            else
                local elapsed = math.max(0, now - last_refill)
                tokens = math.min(capacity, tokens + (elapsed * refill_rate))
                last_refill = now
            end

            local allowed = 0
            local retry_after = 0

            if tokens >= cost then
                tokens = tokens - cost
                allowed = 1
                retry_after = 0
            else
                allowed = 0
                local missing = cost - tokens
                retry_after = math.ceil(missing / refill_rate)
            end

            redis.call('HSET', key, 'tokens', tokens, 'last_refill', last_refill)
            local ttl = math.max(60, math.ceil((capacity - tokens) / refill_rate) * 2)
            redis.call('EXPIRE', key, ttl)

            return {allowed, math.floor(tokens), retry_after, capacity}
            """;

    private final ReactiveRedisOperations<String, String> redisOperations;
    private final SmartProxyProperties properties;
    private final RedisScript<List> script;

    @Autowired
    public RedisTokenBucketRateLimiter(@Autowired(required = false) ReactiveRedisOperations<String, String> redisOperations,
                                       SmartProxyProperties properties) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, List.class);
    }

    public RedisTokenBucketRateLimiter(ReactiveRedisOperations<String, String> redisOperations,
                                       SmartProxyProperties properties,
                                       RedisScript<List> script) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = script != null ? script : new DefaultRedisScript<>(TOKEN_BUCKET_LUA, List.class);
    }

    @Override
    public RateLimiterAlgorithmType getAlgorithmType() {
        return RateLimiterAlgorithmType.TOKEN_BUCKET;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        if (key == null || policy == null) {
            return Mono.just(RateLimitResult.permitted(100, 100, 0, key, getAlgorithmType()));
        }

        int capacity = Math.max(1, policy.burstCapacity());
        int replenishRate = Math.max(1, policy.replenishRate());
        String redisKeyPrefix = properties.getRatelimiter().getRedisKeyPrefix();
        String fullRedisKey = redisKeyPrefix + "tb:" + key;

        if (redisOperations == null) {
            log.trace("Redis operations not available. Permitting under fallback mode.");
            return Mono.just(RateLimitResult.permitted(capacity, capacity, 0, key, getAlgorithmType()));
        }

        return redisOperations.execute(
                script,
                Collections.singletonList(fullRedisKey),
                List.of(String.valueOf(capacity), String.valueOf(replenishRate), "1")
        ).next()
        .map(rawResult -> parseResult(rawResult, capacity, key))
        .onErrorResume(throwable -> {
            log.warn("Redis token bucket error for key '{}': {}. Applying fallback mode: {}",
                    key, throwable.getMessage(), properties.getRatelimiter().getFallbackMode());
            if ("FAIL_CLOSED".equalsIgnoreCase(properties.getRatelimiter().getFallbackMode())) {
                return Mono.just(RateLimitResult.throttled(capacity, 1, key, getAlgorithmType()));
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
