package com.smartproxy.core.filter;

import com.smartproxy.core.model.GatewayContext;
import reactor.core.publisher.Mono;

/**
 * Contract for all custom reactive filters in the gateway execution pipeline.
 */
public interface GatewayFilter {

    /**
     * Executes filter logic and invokes the subsequent chain filter.
     */
    Mono<Void> filter(GatewayContext context, GatewayFilterChain chain);

    /**
     * Returns the execution order of the filter (lower values run first).
     */
    default int getOrder() {
        return 0;
    }
}
