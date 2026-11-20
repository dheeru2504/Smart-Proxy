package com.smartproxy.circuitbreaker;

import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * High-performance, reactive, concurrency-safe custom circuit breaker.
 * Implements CLOSED, OPEN, HALF_OPEN states with cool-down, limited trial probes, and metrics.
 */
public class CustomCircuitBreaker implements CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CustomCircuitBreaker.class);

    private final String name;
    private final CircuitBreakerConfig config;
    private final SlidingWindowMetrics metrics;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile Instant openStateTimestamp = Instant.MIN;

    // Half-Open state concurrency tracking
    private final AtomicInteger halfOpenPermits = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicInteger halfOpenFailures = new AtomicInteger(0);

    public CustomCircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config != null ? config : defaultDto(name);
        this.metrics = new SlidingWindowMetrics(this.config.slidingWindowSize());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CircuitState getState() {
        checkAndTransitionIfCoolDownElapsed();
        return state.get();
    }

    @Override
    public CircuitBreakerConfig getConfig() {
        return config;
    }

    @Override
    public <T> Mono<T> execute(Supplier<Mono<T>> publisherSupplier) {
        return Mono.defer(() -> {
            checkAndTransitionIfCoolDownElapsed();

            CircuitState currentState = state.get();

            if (currentState == CircuitState.OPEN) {
                return Mono.error(GatewayException.serviceUnavailable(
                        "Circuit breaker is OPEN for service '" + name + "'. Fast-failing incoming request."
                ));
            }

            if (currentState == CircuitState.HALF_OPEN) {
                int remaining;
                do {
                    remaining = halfOpenPermits.get();
                    if (remaining <= 0) {
                        return Mono.error(GatewayException.serviceUnavailable(
                                "Circuit breaker is HALF_OPEN for service '" + name + "'. Maximum trial probe calls reached."
                        ));
                    }
                } while (!halfOpenPermits.compareAndSet(remaining, remaining - 1));
            }

            long startTime = System.currentTimeMillis();

            return publisherSupplier.get()
                    .doOnSuccess(res -> {
                        long duration = System.currentTimeMillis() - startTime;
                        onSuccess(duration);
                    })
                    .doOnError(err -> {
                        long duration = System.currentTimeMillis() - startTime;
                        onError(duration, err);
                    });
        });
    }

    @Override
    public void onSuccess(long durationMs) {
        CircuitState currentState = state.get();

        if (currentState == CircuitState.CLOSED) {
            boolean isSlow = durationMs >= config.slowCallDurationThresholdMs();
            metrics.recordSuccess(isSlow);
            evaluateClosedStateThresholds();
        } else if (currentState == CircuitState.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            log.info("Circuit breaker '{}' trial probe succeeded: {}/{}",
                    name, successes, config.permittedCallsInHalfOpenState());

            if (successes >= config.permittedCallsInHalfOpenState()) {
                transitionTo(CircuitState.CLOSED);
            }
        }
    }

    @Override
    public void onError(long durationMs, Throwable throwable) {
        CircuitState currentState = state.get();

        if (currentState == CircuitState.CLOSED) {
            metrics.recordFailure();
            evaluateClosedStateThresholds();
        } else if (currentState == CircuitState.HALF_OPEN) {
            halfOpenFailures.incrementAndGet();
            log.warn("Circuit breaker '{}' trial probe failed ({}) -> Transitioning back to OPEN",
                    name, throwable.getMessage());
            transitionTo(CircuitState.OPEN);
        }
    }

    @Override
    public synchronized void transitionTo(CircuitState targetState) {
        CircuitState previous = state.getAndSet(targetState);
        if (previous != targetState) {
            log.warn("Circuit breaker '{}' transitioned from {} -> {}", name, previous, targetState);

            if (targetState == CircuitState.OPEN) {
                this.openStateTimestamp = Instant.now();
            } else if (targetState == CircuitState.HALF_OPEN) {
                this.halfOpenPermits.set(config.permittedCallsInHalfOpenState());
                this.halfOpenSuccesses.set(0);
                this.halfOpenFailures.set(0);
            } else if (targetState == CircuitState.CLOSED) {
                this.metrics.reset();
                this.halfOpenPermits.set(0);
                this.halfOpenSuccesses.set(0);
                this.halfOpenFailures.set(0);
            }
        }
    }

    private void evaluateClosedStateThresholds() {
        SlidingWindowMetrics.Snapshot snapshot = metrics.getSnapshot();
        if (snapshot.totalCalls() >= config.minimumNumberOfCalls()) {
            if (snapshot.failureRatePercentage() >= config.failureRateThresholdPercentage() ||
                snapshot.slowCallRatePercentage() >= config.slowCallRateThresholdPercentage()) {
                log.warn("Circuit breaker '{}' tripped! Fail rate: {}% (threshold: {}%), Slow rate: {}% (threshold: {}%)",
                        name, snapshot.failureRatePercentage(), config.failureRateThresholdPercentage(),
                        snapshot.slowCallRatePercentage(), config.slowCallRateThresholdPercentage());
                transitionTo(CircuitState.OPEN);
            }
        }
    }

    private void checkAndTransitionIfCoolDownElapsed() {
        if (state.get() == CircuitState.OPEN) {
            Instant coolDownExpiry = openStateTimestamp.plusSeconds(config.waitDurationInOpenStateSec());
            if (Instant.now().isAfter(coolDownExpiry)) {
                transitionTo(CircuitState.HALF_OPEN);
            }
        }
    }

    private static CircuitBreakerConfig defaultDto(String name) {
        return new CircuitBreakerConfig(
                null,
                name,
                20,
                5,
                50.0,
                50.0,
                2000,
                10,
                3,
                null
        );
    }
}
