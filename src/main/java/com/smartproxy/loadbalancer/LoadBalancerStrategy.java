package com.smartproxy.loadbalancer;

import com.smartproxy.core.model.GatewayContext;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Strategy interface for custom Layer-7 load balancing distribution algorithms.
 */
public interface LoadBalancerStrategy {

    /**
     * Identifies the algorithm strategy type.
     */
    LoadBalancerStrategyType getStrategyType();

    /**
     * Selects an optimal upstream instance from the available candidate pool.
     */
    Mono<UpstreamInstance> select(List<UpstreamInstance> instances, GatewayContext context);
}
