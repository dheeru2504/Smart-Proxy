package com.smartproxy.loadbalancer.strategy;

import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.loadbalancer.LoadBalancerStrategy;
import com.smartproxy.loadbalancer.LoadBalancerStrategyType;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random selection Layer-7 load balancing strategy.
 */
@Component
public class RandomStrategy implements LoadBalancerStrategy {

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.RANDOM;
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

        int index = ThreadLocalRandom.current().nextInt(healthy.size());
        return Mono.just(healthy.get(index));
    }
}
