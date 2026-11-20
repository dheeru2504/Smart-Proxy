package com.smartproxy.ratelimiter;

import java.util.UUID;

public record RateLimitPolicy(
        UUID id,
        String name,
        RateLimiterAlgorithmType algorithm,
        int replenishRate,
        int burstCapacity,
        int windowDurationSec,
        String limitBy // IP, API_KEY, USER, ROUTE
) {}
