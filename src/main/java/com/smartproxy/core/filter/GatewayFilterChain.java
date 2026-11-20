package com.smartproxy.core.filter;

import com.smartproxy.core.model.GatewayContext;
import reactor.core.publisher.Mono;

/**
 * Encapsulates the execution sequence of ordered GatewayFilters.
 */
public interface GatewayFilterChain {

    /**
     * Propagates execution to the next filter in the pipeline.
     */
    Mono<Void> filter(GatewayContext context);
}
