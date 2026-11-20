package com.smartproxy.loadbalancer.strategy;

import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.loadbalancer.LoadBalancerStrategy;
import com.smartproxy.loadbalancer.LoadBalancerStrategyType;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe Round-Robin Layer-7 load balancing strategy.
 */
@Component
public class RoundRobinStrategy implements LoadBalancerStrategy {

    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.ROUND_ROBIN;
    }

    @Override
    public Mono<UpstreamInstance> select(List<UpstreamInstance> instances, GatewayContext context) {
        if (instances == null || instances.isEmpty()) {
            return Mono.empty();
        }

        List<UpstreamInstance> healthy = instances.stream()
                .filter(UpstreamInstance::isAvailable)
                .toList();

        if (healthy.isEmpty()) {
            return Mono.empty();
        }

        if (healthy.size() == 1) {
            return Mono.just(healthy.get(0));
        }

        int index = (position.getAndIncrement() & Integer.MAX_VALUE) % healthy.size();
        return Mono.just(healthy.get(index));
    }
}
