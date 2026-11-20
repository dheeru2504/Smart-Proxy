package com.smartproxy.core.proxy.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReactiveRetryExecutorTest {

    private ReactiveRetryExecutor retryExecutor;

    @BeforeEach
    void setUp() {
        retryExecutor = new ReactiveRetryExecutor();
    }

    @Test
    void shouldRetryTransientFailureAndSucceedOnSubsequentAttempt() {
        AtomicInteger attemptCounter = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10L, 50L, 2.0, 100);

        Mono<String> operation = retryExecutor.executeWithRetry(
                () -> {
                    int attempt = attemptCounter.incrementAndGet();
                    if (attempt == 1) {
                        return Mono.error(new ConnectException("Upstream connection refused"));
                    }
                    return Mono.just("SUCCESS_ON_RETRY");
                },
                HttpMethod.GET,
                new HttpHeaders(),
                policy,
                5000L,
                "trace-123"
        );

        StepVerifier.create(operation)
                .expectNext("SUCCESS_ON_RETRY")
                .verifyComplete();

        assertEquals(2, attemptCounter.get(), "Operation must have executed twice (1 retry)");
    }

    @Test
    void shouldNotRetrySideEffectingPostWithoutIdempotencyKey() {
        AtomicInteger attemptCounter = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10L, 50L, 2.0, 100);

        Mono<String> operation = retryExecutor.executeWithRetry(
                () -> {
                    attemptCounter.incrementAndGet();
                    return Mono.error(new ConnectException("Network error on POST"));
                },
                HttpMethod.POST,
                new HttpHeaders(), // No Idempotency-Key
                policy,
                5000L,
                "trace-456"
        );

        StepVerifier.create(operation)
                .expectError(ConnectException.class)
                .verify();

        assertEquals(1, attemptCounter.get(), "Side-effecting POST must never be retried without idempotency header");
    }
}
