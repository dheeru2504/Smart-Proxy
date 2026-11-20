package com.smartproxy.ratelimiter;

/**
 * Result returned by the atomic rate limiter evaluation.
 */
public record RateLimitResult(
        boolean allowed,
        long limit,
        long remainingTokens,
        long ttlSeconds,
        String limitKey,
        RateLimiterAlgorithmType algorithm
) {
    public static RateLimitResult permitted(long limit, long remainingTokens, long ttlSeconds, String key, RateLimiterAlgorithmType algorithm) {
        return new RateLimitResult(true, limit, remainingTokens, ttlSeconds, key, algorithm);
    }

    public static RateLimitResult throttled(long limit, long ttlSeconds, String key, RateLimiterAlgorithmType algorithm) {
        return new RateLimitResult(false, limit, 0, ttlSeconds, key, algorithm);
    }
}
