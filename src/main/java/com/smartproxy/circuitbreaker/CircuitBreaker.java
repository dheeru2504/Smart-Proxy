package com.smartproxy.circuitbreaker;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Custom reactive circuit breaker contract.
 */
public interface CircuitBreaker {

    String getName();

    CircuitState getState();

    CircuitBreakerConfig getConfig();

    /**
     * Decorates and executes a reactive operation through the circuit breaker state machine.
     */
    <T> Mono<T> execute(Supplier<Mono<T>> publisherSupplier);

    /**
     * Records a successful execution.
     */
    void onSuccess(long durationMs);

    /**
     * Records a failed execution.
     */
    void onError(long durationMs, Throwable throwable);

    /**
     * Manually forces the circuit breaker state (for admin override / emergency trip).
     */
    void transitionTo(CircuitState targetState);
}
