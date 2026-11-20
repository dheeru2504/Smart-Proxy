package com.smartproxy.core.proxy.retry;

import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Executes reactive upstream calls with bounded retries, exponential backoff with jitter,
 * and end-to-end deadline budget checking.
 */
@Component
public class ReactiveRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRetryExecutor.class);

    /**
     * Executes a supplier with bounded retries, backoff, and deadline awareness.
     *
     * @param operationSupplier supplier producing the reactive upstream call
     * @param method            HTTP method for idempotency checks
     * @param headers           HTTP headers (e.g. Idempotency-Key)
     * @param policy            configured retry policy
     * @param deadlineMs        overall timeout/deadline budget in ms
     * @param traceId           request trace ID
     * @return resilient response Mono
     */
    public <T> Mono<T> executeWithRetry(Supplier<Mono<T>> operationSupplier,
                                        HttpMethod method,
                                        HttpHeaders headers,
                                        RetryPolicy policy,
                                        long deadlineMs,
                                        String traceId) {
        long startTime = System.currentTimeMillis();
        boolean isRetryable = policy.isOperationRetryable(method, headers);

        return executeAttempt(operationSupplier, method, headers, policy, 1, isRetryable, startTime, deadlineMs, traceId);
    }

    private <T> Mono<T> executeAttempt(Supplier<Mono<T>> operationSupplier,
                                       HttpMethod method,
                                       HttpHeaders headers,
                                       RetryPolicy policy,
                                       int attempt,
                                       boolean isRetryable,
                                       long startTime,
                                       long deadlineMs,
                                       String traceId) {
        return Mono.defer(operationSupplier)
                .onErrorResume(error -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remainingDeadline = deadlineMs - elapsed;

                    // If not retryable or max attempts exhausted or deadline passed -> propagate error
                    if (!isRetryable || attempt >= policy.maxAttempts() || remainingDeadline <= 0 || !policy.isErrorRetryable(error)) {
                        if (!isRetryable && (method == HttpMethod.POST || method == HttpMethod.PATCH)) {
                            log.debug("[{}] Not retrying side-effecting {} request without Idempotency-Key header", traceId, method);
                        }
                        return Mono.error(error);
                    }

                    long backoffMs = policy.calculateBackoffWithJitter(attempt);

                    if (backoffMs >= remainingDeadline) {
                        log.warn("[{}] Aborting retry attempt {}/{}: Backoff delay ({}ms) exceeds remaining deadline ({}ms)",
                                traceId, attempt + 1, policy.maxAttempts(), backoffMs, remainingDeadline);
                        return Mono.error(GatewayException.gatewayTimeout("Gateway deadline exceeded during retries."));
                    }

                    log.warn("[{}] Retrying {} request (attempt {}/{} after {}ms backoff). Cause: {}",
                            traceId, method, attempt + 1, policy.maxAttempts(), backoffMs, error.getMessage());

                    return Mono.delay(Duration.ofMillis(backoffMs))
                            .flatMap(tick -> executeAttempt(
                                    operationSupplier, method, headers, policy, attempt + 1,
                                    isRetryable, startTime, deadlineMs, traceId
                            ));
                });
    }
}
