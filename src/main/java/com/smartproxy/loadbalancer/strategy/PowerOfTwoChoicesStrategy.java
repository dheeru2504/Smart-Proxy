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
 * Power of Two Random Choices (P2C) Layer-7 load balancing strategy.
 * Picks two candidate instances at random and selects the one with fewer active in-flight connections.
 * Effectively mitigates thundering-herd issues in large clusters with $O(1)$ constant-time decision overhead.
 */
@Component
public class PowerOfTwoChoicesStrategy implements LoadBalancerStrategy {

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.POWER_OF_TWO_CHOICES;
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

        int size = healthy.size();
        if (size == 1) {
            return Mono.just(healthy.get(0));
        }

        if (size == 2) {
            UpstreamInstance first = healthy.get(0);
            UpstreamInstance second = healthy.get(1);
            return Mono.just(pickBetter(first, second));
        }

        // Pick two distinct random indices
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int idx1 = random.nextInt(size);
        int idx2 = random.nextInt(size - 1);
        if (idx2 >= idx1) {
            idx2++;
        }

        UpstreamInstance first = healthy.get(idx1);
        UpstreamInstance second = healthy.get(idx2);

        return Mono.just(pickBetter(first, second));
    }

    private UpstreamInstance pickBetter(UpstreamInstance a, UpstreamInstance b) {
        int loadA = a.getActiveConnections().get();
        int loadB = b.getActiveConnections().get();

        if (loadA < loadB) {
            return a;
        } else if (loadB < loadA) {
            return b;
        } else {
            // If active load is equal, prefer higher weight
            return a.getWeight() >= b.getWeight() ? a : b;
        }
    }
}
