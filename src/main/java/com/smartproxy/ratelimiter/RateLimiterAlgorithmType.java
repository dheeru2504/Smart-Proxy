package com.smartproxy.ratelimiter;

public enum RateLimiterAlgorithmType {
    TOKEN_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    LEAKY_BUCKET
}
