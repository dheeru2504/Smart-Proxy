package com.smartproxy.loadbalancer.strategy;

import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.loadbalancer.LoadBalancerStrategy;
import com.smartproxy.loadbalancer.LoadBalancerStrategyType;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * IP-Hash sticky load balancing strategy.
 */
@Component
public class IpHashStrategy implements LoadBalancerStrategy {

    @Override
    public LoadBalancerStrategyType getStrategyType() {
        return LoadBalancerStrategyType.IP_HASH;
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

        String clientIp = resolveClientIp(context);
        int hash = clientIp.hashCode();
        int index = Math.abs(hash % healthy.size());
        return Mono.just(healthy.get(index));
    }

    private String resolveClientIp(GatewayContext context) {
        if (context == null || context.getExchange() == null) {
            return "127.0.0.1";
        }
        String xForwardedFor = context.getExchange().getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = context.getExchange().getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "127.0.0.1";
    }
}
