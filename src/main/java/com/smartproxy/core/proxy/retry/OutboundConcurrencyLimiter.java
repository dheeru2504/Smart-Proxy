package com.smartproxy.core.proxy.retry;

import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks outbound in-flight request concurrency per backend service.
 * Enforces overload protection by rejecting traffic when upstream concurrency limits are saturated.
 */
@Component
public class OutboundConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(OutboundConcurrencyLimiter.class);

    private final Map<String, AtomicInteger> activeRequestsPerService = new ConcurrentHashMap<>();
    private final int defaultMaxConcurrency;

    public OutboundConcurrencyLimiter() {
        this(100);
    }

    public OutboundConcurrencyLimiter(int defaultMaxConcurrency) {
        this.defaultMaxConcurrency = Math.max(1, defaultMaxConcurrency);
    }

    /**
     * Executes the given publisher while enforcing the service concurrency limit.
     */
    public <T> Mono<T> executeWithConcurrencyLimit(String serviceId, int maxConcurrency, Mono<T> publisher) {
        if (serviceId == null || serviceId.isBlank()) {
            return publisher;
        }

        int limit = maxConcurrency > 0 ? maxConcurrency : defaultMaxConcurrency;
        AtomicInteger counter = activeRequestsPerService.computeIfAbsent(serviceId, k -> new AtomicInteger(0));

        return Mono.defer(() -> {
            int current = counter.incrementAndGet();
            if (current > limit) {
                counter.decrementAndGet();
                log.warn("Overload rejection for service '{}': Active {} >= Limit {}", serviceId, current - 1, limit);
                return Mono.error(GatewayException.serviceUnavailable(
                        "Service '" + serviceId + "' is overloaded. Outbound concurrency limit (" + limit + ") exceeded."
                ));
            }

            return publisher
                    .doFinally(signalType -> {
                        counter.decrementAndGet();
                    });
        });
    }

    public int getActiveCount(String serviceId) {
        AtomicInteger counter = activeRequestsPerService.get(serviceId);
        return counter != null ? counter.get() : 0;
    }

    public void reset() {
        activeRequestsPerService.clear();
    }
}
