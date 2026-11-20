package com.smartproxy.core.proxy.retry;

import com.smartproxy.core.foundation.error.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class OutboundConcurrencyLimiterTest {

    private OutboundConcurrencyLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new OutboundConcurrencyLimiter(2);
    }

    @Test
    void shouldPermitRequestsWithinConcurrencyLimitAndDecrementOnComplete() {
        assertEquals(0, limiter.getActiveCount("service-a"));

        Mono<String> op = limiter.executeWithConcurrencyLimit("service-a", 2, Mono.just("OK"));

        StepVerifier.create(op)
                .expectNext("OK")
                .verifyComplete();

        // Concurrency count must decrement back to 0 on completion
        assertEquals(0, limiter.getActiveCount("service-a"));
    }

    @Test
    void shouldRejectTrafficWhenConcurrencyLimitExceeded() {
        // Concurrency limit = 1
        // Simulate in-flight active request
        limiter.executeWithConcurrencyLimit("payment-service", 1, Mono.never()).subscribe();

        assertEquals(1, limiter.getActiveCount("payment-service"));

        // Second concurrent request must be rejected immediately with 503
        Mono<String> secondRequest = limiter.executeWithConcurrencyLimit("payment-service", 1, Mono.just("SHOULD_FAIL"));

        StepVerifier.create(secondRequest)
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();
    }
}
