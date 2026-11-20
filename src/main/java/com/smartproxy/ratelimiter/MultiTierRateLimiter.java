package com.smartproxy.ratelimiter;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface contract for multi-tier hierarchical rate limiters.
 */
public interface MultiTierRateLimiter {

    /**
     * Atomically evaluates all requested policy tiers in a single operation.
     */
    Mono<RateLimitResult> isAllowed(List<RedisMultiTierRateLimiter.TierRequest> tiers);
}
