package com.smartproxy.ratelimiter;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory resolving rate limiter strategy beans dynamically.
 */
@Component
public class RateLimiterFactory {

    private final Map<RateLimiterAlgorithmType, RateLimiter> limiterMap = new EnumMap<>(RateLimiterAlgorithmType.class);
    private final RateLimiter defaultLimiter;

    public RateLimiterFactory(List<RateLimiter> limiters) {
        RateLimiter tokenBucketLimiter = null;
        RateLimiter fixedWindowLimiter = null;

        if (limiters != null) {
            for (RateLimiter limiter : limiters) {
                limiterMap.put(limiter.getAlgorithmType(), limiter);
                if (limiter.getAlgorithmType() == RateLimiterAlgorithmType.TOKEN_BUCKET) {
                    tokenBucketLimiter = limiter;
                } else if (limiter.getAlgorithmType() == RateLimiterAlgorithmType.FIXED_WINDOW) {
                    fixedWindowLimiter = limiter;
                }
            }
        }

        if (tokenBucketLimiter != null) {
            this.defaultLimiter = tokenBucketLimiter;
        } else if (fixedWindowLimiter != null) {
            this.defaultLimiter = fixedWindowLimiter;
        } else if (limiters != null && !limiters.isEmpty()) {
            this.defaultLimiter = limiters.get(0);
        } else {
            this.defaultLimiter = null;
        }
    }

    public RateLimiter getLimiter(RateLimiterAlgorithmType type) {
        if (type == null) {
            return defaultLimiter;
        }
        return limiterMap.getOrDefault(type, defaultLimiter);
    }
}
