package com.smartproxy.core.proxy.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy(3, 20L, 500L, 2.0, 50);
    }

    @Test
    void shouldAllowSafeAndIdempotentMethods() {
        HttpHeaders headers = new HttpHeaders();

        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.GET, headers));
        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.HEAD, headers));
        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.PUT, headers));
        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.DELETE, headers));
        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.OPTIONS, headers));
    }

    @Test
    void shouldDisallowPostWithoutIdempotencyKey() {
        HttpHeaders emptyHeaders = new HttpHeaders();
        assertFalse(retryPolicy.isOperationRetryable(HttpMethod.POST, emptyHeaders),
                "POST must not be retried without idempotency header");

        assertFalse(retryPolicy.isOperationRetryable(HttpMethod.PATCH, emptyHeaders),
                "PATCH must not be retried without idempotency header");
    }

    @Test
    void shouldAllowPostWithIdempotencyKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RetryPolicy.HEADER_IDEMPOTENCY_KEY, "req-uuid-12345");

        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.POST, headers),
                "POST with Idempotency-Key must be allowed to retry");

        HttpHeaders xHeaders = new HttpHeaders();
        xHeaders.set(RetryPolicy.HEADER_X_IDEMPOTENCY_KEY, "x-key-999");
        assertTrue(retryPolicy.isOperationRetryable(HttpMethod.POST, xHeaders));
    }

    @Test
    void shouldIdentifyTransientErrors() {
        assertTrue(retryPolicy.isErrorRetryable(new ConnectException("Connection refused")));
        assertTrue(retryPolicy.isErrorRetryable(new SocketTimeoutException("Read timed out")));
        assertFalse(retryPolicy.isErrorRetryable(new IllegalArgumentException("Bad request parameter")));
    }

    @Test
    void shouldCalculateJitteredBackoffWithinBounds() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            long backoff = retryPolicy.calculateBackoffWithJitter(attempt);
            assertTrue(backoff >= 0 && backoff <= 500, "Backoff must stay within max bounds");
        }
    }
}
