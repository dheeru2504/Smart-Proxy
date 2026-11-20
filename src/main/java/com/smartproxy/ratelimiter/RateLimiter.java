package com.smartproxy.ratelimiter;

import reactor.core.publisher.Mono;

/**
 * Contract for custom distributed rate limiters.
 */
public interface RateLimiter {

    /**
     * Identifies the rate limiting algorithm type.
     */
    RateLimiterAlgorithmType getAlgorithmType();

    /**
     * Evaluates whether a request with the given key is allowed under the policy.
     */
    Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy);
}
