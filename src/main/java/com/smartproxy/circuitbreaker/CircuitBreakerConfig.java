package com.smartproxy.circuitbreaker;

import java.util.UUID;

public record CircuitBreakerConfig(
        UUID id,
        String name,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        double failureRateThresholdPercentage,
        double slowCallRateThresholdPercentage,
        int slowCallDurationThresholdMs,
        int waitDurationInOpenStateSec,
        int permittedCallsInHalfOpenState,
        String fallbackUri
) {}
