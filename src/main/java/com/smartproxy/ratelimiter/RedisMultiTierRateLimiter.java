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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes multi-tier hierarchical rate limit policies atomically in a single Redis Lua script.
 * Enforces strict all-or-nothing semantics: if any tier fails, zero tokens are consumed from any tier.
 */
@Component
public class RedisMultiTierRateLimiter implements MultiTierRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisMultiTierRateLimiter.class);

    public static final String MULTI_TOKEN_BUCKET_LUA = """
            local num_keys = tonumber(ARGV[1])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) + (tonumber(time[2]) / 1000000.0)

            local capacities = {}
            local refill_rates = {}
            local costs = {}
            local simulated_tokens = {}

            -- Read arguments
            local arg_offset = 1
            for i = 1, num_keys do
                capacities[i] = tonumber(ARGV[arg_offset + i])
            end
            arg_offset = arg_offset + num_keys
            for i = 1, num_keys do
                refill_rates[i] = tonumber(ARGV[arg_offset + i])
            end
            arg_offset = arg_offset + num_keys
            for i = 1, num_keys do
                costs[i] = tonumber(ARGV[arg_offset + i]) or 1
            end

            -- Phase 1: Verification & continuous refill calculation across all tiers
            local all_allowed = 1
            local min_remaining = 999999999
            local min_capacity = 999999999
            local max_retry_after = 0
            local failing_key_idx = 0

            for i = 1, num_keys do
                local key = KEYS[i]
                local capacity = capacities[i]
                local refill_rate = refill_rates[i]
                local cost = costs[i]

                if capacity < min_capacity then
                    min_capacity = capacity
                end

                local state = redis.call('HMGET', key, 'tokens', 'last_refill')
                local tokens = tonumber(state[1])
                local last_refill = tonumber(state[2])

                if tokens == nil or last_refill == nil then
                    tokens = capacity
                    last_refill = now
                else
                    local elapsed = math.max(0, now - last_refill)
                    tokens = math.min(capacity, tokens + (elapsed * refill_rate))
                end

                simulated_tokens[i] = tokens

                if tokens < cost then
                    all_allowed = 0
                    local missing = cost - tokens
                    local wait_sec = math.ceil(missing / refill_rate)
                    if wait_sec > max_retry_after then
                        max_retry_after = wait_sec
                    end
                    if failing_key_idx == 0 then
                        failing_key_idx = i
                    end
                else
                    local rem = math.floor(tokens - cost)
                    if rem < min_remaining then
                        min_remaining = rem
                    end
                end
            end

            -- Phase 2: Atomic Decision & Commit
            if all_allowed == 0 then
                -- Rejection: Do not touch any key state to prevent partial consumption leaks
                return {0, 0, max_retry_after, min_capacity, failing_key_idx}
            else
                -- Allowed: Commit state deduction and TTL updates for all tiers
                for i = 1, num_keys do
                    local key = KEYS[i]
                    local capacity = capacities[i]
                    local refill_rate = refill_rates[i]
                    local cost = costs[i]
                    local final_tokens = simulated_tokens[i] - cost

                    redis.call('HSET', key, 'tokens', final_tokens, 'last_refill', now)
                    local ttl = math.max(60, math.ceil((capacity - final_tokens) / refill_rate) * 2)
                    redis.call('EXPIRE', key, ttl)
                end
                return {1, min_remaining, 0, min_capacity, 0}
            end
            """;

    private final ReactiveRedisOperations<String, String> redisOperations;
    private final SmartProxyProperties properties;
    private final RedisScript<List> script;

    public record TierRequest(String key, int capacity, int replenishRate, int cost) {}

    @Autowired
    public RedisMultiTierRateLimiter(@Autowired(required = false) ReactiveRedisOperations<String, String> redisOperations,
                                     SmartProxyProperties properties) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(MULTI_TOKEN_BUCKET_LUA, List.class);
    }

    public RedisMultiTierRateLimiter(ReactiveRedisOperations<String, String> redisOperations,
                                     SmartProxyProperties properties,
                                     RedisScript<List> script) {
        this.redisOperations = redisOperations;
        this.properties = properties;
        this.script = script != null ? script : new DefaultRedisScript<>(MULTI_TOKEN_BUCKET_LUA, List.class);
    }

    /**
     * Atomically evaluates all requested policy tiers in a single Redis roundtrip.
     */
    public Mono<RateLimitResult> isAllowed(List<TierRequest> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Mono.just(RateLimitResult.permitted(100, 100, 0, "global", RateLimiterAlgorithmType.TOKEN_BUCKET));
        }

        int numKeys = tiers.size();
        List<String> redisKeys = new ArrayList<>(numKeys);
        List<String> capacities = new ArrayList<>(numKeys);
        List<String> refillRates = new ArrayList<>(numKeys);
        List<String> costs = new ArrayList<>(numKeys);

        String prefix = properties.getRatelimiter().getRedisKeyPrefix() + "tb:";

        int minCapacity = Integer.MAX_VALUE;
        for (TierRequest tier : tiers) {
            redisKeys.add(prefix + tier.key());
            int cap = Math.max(1, tier.capacity());
            capacities.add(String.valueOf(cap));
            refillRates.add(String.valueOf(Math.max(1, tier.replenishRate())));
            costs.add(String.valueOf(Math.max(1, tier.cost())));
            if (cap < minCapacity) {
                minCapacity = cap;
            }
        }

        if (redisOperations == null) {
            log.trace("Redis operations unavailable. Permitting under fallback mode.");
            return Mono.just(RateLimitResult.permitted(minCapacity, minCapacity, 0, tiers.get(0).key(), RateLimiterAlgorithmType.TOKEN_BUCKET));
        }

        List<String> args = new ArrayList<>(1 + (numKeys * 3));
        args.add(String.valueOf(numKeys));
        args.addAll(capacities);
        args.addAll(refillRates);
        args.addAll(costs);

        int fallbackCapacity = minCapacity;
        return redisOperations.execute(script, redisKeys, args).next()
                .map(rawResult -> parseMultiTierResult(rawResult, fallbackCapacity, tiers.get(0).key()))
                .onErrorResume(throwable -> {
                    log.warn("Redis multi-tier rate limiter error: {}. Applying fallback: {}",
                            throwable.getMessage(), properties.getRatelimiter().getFallbackMode());
                    if ("FAIL_CLOSED".equalsIgnoreCase(properties.getRatelimiter().getFallbackMode())) {
                        return Mono.just(RateLimitResult.throttled(fallbackCapacity, 1, tiers.get(0).key(), RateLimiterAlgorithmType.TOKEN_BUCKET));
                    }
                    return Mono.just(RateLimitResult.permitted(fallbackCapacity, fallbackCapacity, 0, tiers.get(0).key(), RateLimiterAlgorithmType.TOKEN_BUCKET));
                });
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult parseMultiTierResult(Object rawResult, int fallbackCapacity, String primaryKey) {
        if (rawResult instanceof List<?> list && list.size() >= 4) {
            long allowed = ((Number) list.get(0)).longValue();
            long minRemaining = ((Number) list.get(1)).longValue();
            long maxRetryAfter = ((Number) list.get(2)).longValue();
            long capacity = ((Number) list.get(3)).longValue();

            if (allowed == 1L) {
                return RateLimitResult.permitted(capacity, minRemaining, 0, primaryKey, RateLimiterAlgorithmType.TOKEN_BUCKET);
            } else {
                return RateLimitResult.throttled(capacity, maxRetryAfter, primaryKey, RateLimiterAlgorithmType.TOKEN_BUCKET);
            }
        }

        return RateLimitResult.permitted(fallbackCapacity, fallbackCapacity, 0, primaryKey, RateLimiterAlgorithmType.TOKEN_BUCKET);
    }
}
