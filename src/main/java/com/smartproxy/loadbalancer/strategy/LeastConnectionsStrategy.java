package com.smartproxy.loadbalancer.strategy;

import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.loadbalancer.LoadBalancerStrategy;
import com.smartproxy.loadbalancer.LoadBalancerStrategyType;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Least Active Connections Layer-7 load balancing strategy.
 * Directs traffic to the server instance with the lowest number of concurrent in-flight requests.
 */
@Component
public class LeastConnectionsStrategy implements LoadBalancerStrategy {

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.LEAST_CONNECTIONS;
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

        UpstreamInstance leastLoaded = healthy.get(0);
        int minActive = leastLoaded.getActiveConnections().get();

        for (int i = 1; i < healthy.size(); i++) {
            UpstreamInstance current = healthy.get(i);
            int currentActive = current.getActiveConnections().get();

            if (currentActive < minActive) {
                minActive = currentActive;
                leastLoaded = current;
            } else if (currentActive == minActive && current.getWeight() > leastLoaded.getWeight()) {
                // Break tie using higher server capacity weight
                leastLoaded = current;
            }
        }

        return Mono.just(leastLoaded);
    }
}
