package com.smartproxy.loadbalancer;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service managing L7 load balancing decisions and thread-safe in-flight connection lifecycle tracking.
 */
@Service
public class LoadBalancerService {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerService.class);

    private final UpstreamPoolRegistry poolRegistry;
    private final LoadBalancerStrategyFactory strategyFactory;

    public LoadBalancerService(UpstreamPoolRegistry poolRegistry, LoadBalancerStrategyFactory strategyFactory) {
        this.poolRegistry = poolRegistry;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Selects an optimal upstream instance using the specified load balancing strategy.
     */
    public Mono<UpstreamInstance> choose(String serviceId, String strategyName, GatewayContext context) {
        if (serviceId == null || serviceId.isBlank()) {
            return Mono.error(GatewayException.badRequest("Cannot load balance without a target serviceId."));
        }

        List<UpstreamInstance> candidates = poolRegistry.getInstances(serviceId);
        if (candidates.isEmpty()) {
            return Mono.error(GatewayException.serviceUnavailable("No upstream instances configured for service: " + serviceId));
        }

        LoadBalancerStrategy strategy = strategyFactory.getStrategy(strategyName);

        return strategy.select(candidates, context)
                .switchIfEmpty(Mono.error(GatewayException.serviceUnavailable(
                        "All upstream instances are currently unhealthy for service: " + serviceId)));
    }

    /**
     * Wraps a reactive upstream network execution with thread-safe in-flight connection counters and success/failure metrics.
     */
    public <T> Mono<T> trackExecution(UpstreamInstance instance, Mono<T> executionMono) {
        if (instance == null) {
            return executionMono;
        }

        return Mono.defer(() -> {
            instance.incrementActiveConnections();
            long startNanos = System.nanoTime();

            return executionMono
                    .doOnSuccess(res -> instance.recordSuccess())
                    .doOnError(err -> {
                        instance.recordFailure();
                        log.warn("Upstream call failed for instance {}:{} - Active remaining: {} - Error: {}",
                                instance.getHost(), instance.getPort(), instance.getActiveConnections().get(), err.getMessage());
                    })
                    .doFinally(signalType -> {
                        int remaining = instance.decrementActiveConnections();
                        log.trace("Connection released for {}:{} - Active remaining: {}",
                                instance.getHost(), instance.getPort(), remaining);
                    });
        });
    }
}
