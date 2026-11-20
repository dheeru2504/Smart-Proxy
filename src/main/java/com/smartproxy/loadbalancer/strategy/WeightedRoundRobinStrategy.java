package com.smartproxy.loadbalancer.strategy;

import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.loadbalancer.LoadBalancerStrategy;
import com.smartproxy.loadbalancer.LoadBalancerStrategyType;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Smooth Interleaved Weighted Round-Robin load balancing strategy.
 * Prevents clustering bursts to heavy-weight servers while guaranteeing exact mathematical distribution.
 */
@Component
public class WeightedRoundRobinStrategy implements LoadBalancerStrategy {

    private final Object lock = new Object();

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.WEIGHTED_ROUND_ROBIN;
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

        synchronized (lock) {
            int totalWeight = 0;
            UpstreamInstance best = null;
            int maxCurrentWeight = Integer.MIN_VALUE;

            for (UpstreamInstance instance : healthy) {
                int effectiveWeight = Math.max(1, instance.getWeight());
                totalWeight += effectiveWeight;

                // currentWeight += effectiveWeight
                int current = instance.getCurrentWeight().addAndGet(effectiveWeight);

                if (current > maxCurrentWeight) {
                    maxCurrentWeight = current;
                    best = instance;
                }
            }

            if (best != null) {
                // best.currentWeight -= totalWeight
                best.getCurrentWeight().addAndGet(-totalWeight);
                return Mono.just(best);
            }

            return Mono.just(healthy.get(0));
        }
    }
}
