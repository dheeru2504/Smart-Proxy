package com.smartproxy.circuitbreaker;

import com.smartproxy.core.foundation.error.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomCircuitBreakerTest {

    private CircuitBreakerConfig config;
    private CustomCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        config = new CircuitBreakerConfig(
                UUID.randomUUID(),
                "order-service",
                10,       // slidingWindowSize
                4,        // minimumNumberOfCalls
                50.0,     // failureRateThresholdPercentage
                50.0,     // slowCallRateThresholdPercentage
                1000,     // slowCallDurationThresholdMs
                1,        // waitDurationInOpenStateSec (1 second for test speed)
                2,        // permittedCallsInHalfOpenState
                null
        );
        circuitBreaker = new CustomCircuitBreaker("order-service", config);
    }

    @Test
    void shouldStartInClosedStateAndPermitRequests() {
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());

        StepVerifier.create(circuitBreaker.execute(() -> Mono.just("SUCCESS")))
                .expectNext("SUCCESS")
                .verifyComplete();

        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void shouldTripFromClosedToOpenWhenFailureThresholdExceeded() {
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());

        // 2 successes, 2 failures -> 50% fail rate at minimum 4 calls
        circuitBreaker.onSuccess(50);
        circuitBreaker.onSuccess(60);
        circuitBreaker.onError(70, new RuntimeException("500 Internal Error"));
        circuitBreaker.onError(80, new RuntimeException("500 Internal Error"));

        assertEquals(CircuitState.OPEN, circuitBreaker.getState());

        // Any subsequent call should immediately fast-fail with 503 Service Unavailable
        StepVerifier.create(circuitBreaker.execute(() -> Mono.just("SHOULD_NOT_EXECUTE")))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();
    }

    @Test
    void shouldTransitionToHalfOpenAfterCoolDownAndRecoverToClosed() throws InterruptedException {
        // Force state to OPEN
        circuitBreaker.transitionTo(CircuitState.OPEN);
        assertEquals(CircuitState.OPEN, circuitBreaker.getState());

        // Wait for 1 second cool-down
        Thread.sleep(1100);

        // State check triggers cool-down transition to HALF_OPEN
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState());

        // Execute 2 successful trial probes (permittedCallsInHalfOpenState = 2)
        StepVerifier.create(circuitBreaker.execute(() -> Mono.just("PROBE_1")))
                .expectNext("PROBE_1")
                .verifyComplete();

        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState());

        StepVerifier.create(circuitBreaker.execute(() -> Mono.just("PROBE_2")))
                .expectNext("PROBE_2")
                .verifyComplete();

        // After 2 successful probes, circuit breaker must recover to CLOSED!
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void shouldRelapseToOpenIfTrialProbeFailsInHalfOpen() throws InterruptedException {
        circuitBreaker.transitionTo(CircuitState.OPEN);
        Thread.sleep(1100);

        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState());

        // First trial probe fails
        StepVerifier.create(circuitBreaker.execute(() -> Mono.error(new RuntimeException("Probe downstream failure"))))
                .expectError(RuntimeException.class)
                .verify();

        // Must immediately trip back to OPEN
        assertEquals(CircuitState.OPEN, circuitBreaker.getState());
    }
}
