package com.smartproxy.core.proxy.retry;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * Encapsulates bounded retry policy, idempotency rules, and exponential backoff calculations.
 */
public record RetryPolicy(
        int maxAttempts,
        long initialBackoffMs,
        long maxBackoffMs,
        double backoffMultiplier,
        int maxOutboundConcurrency
) {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_X_IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private static final Set<HttpMethod> SAFE_OR_IDEMPOTENT_METHODS = Set.of(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.OPTIONS,
            HttpMethod.TRACE
    );

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 50L, 1000L, 2.0, 100);
    }

    /**
     * Determines whether an HTTP request is retryable based on method idempotency and headers.
     */
    public boolean isOperationRetryable(HttpMethod method, HttpHeaders headers) {
        if (method == null) {
            return false;
        }

        // Safe / Idempotent methods are always retryable
        if (SAFE_OR_IDEMPOTENT_METHODS.contains(method)) {
            return true;
        }

        // POST / PATCH are only retryable if explicit idempotency key is present
        if (headers != null) {
            if (headers.containsKey(HEADER_IDEMPOTENCY_KEY) && !headers.getFirst(HEADER_IDEMPOTENCY_KEY).isBlank()) {
                return true;
            }
            if (headers.containsKey(HEADER_X_IDEMPOTENCY_KEY) && !headers.getFirst(HEADER_X_IDEMPOTENCY_KEY).isBlank()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if a thrown error is transient and eligible for retry.
     */
    public boolean isErrorRetryable(Throwable error) {
        if (error == null) return false;

        if (error instanceof ConnectException || error.getCause() instanceof ConnectException) {
            return true;
        }
        if (error instanceof SocketException || error.getCause() instanceof SocketException) {
            return true;
        }
        if (error instanceof TimeoutException || error.getCause() instanceof TimeoutException) {
            return true;
        }
        if (error instanceof SocketTimeoutException || error.getCause() instanceof SocketTimeoutException) {
            return true;
        }

        return false;
    }

    /**
     * Computes exponential backoff with Full Jitter for the given retry attempt.
     * Full Jitter formula: sleep = rand(0, min(maxBackoff, initialBackoff * multiplier^(attempt-1)))
     */
    public long calculateBackoffWithJitter(int attempt) {
        if (attempt <= 1) {
            return Math.max(1, ThreadLocalRandom.current().nextLong(0, initialBackoffMs + 1));
        }

        double rawBackoff = initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1);
        long cappedBackoff = (long) Math.min(maxBackoffMs, rawBackoff);
        return ThreadLocalRandom.current().nextLong(0, Math.max(1, cappedBackoff) + 1);
    }
}
