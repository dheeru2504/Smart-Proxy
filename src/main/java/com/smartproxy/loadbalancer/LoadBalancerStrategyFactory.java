package com.smartproxy.loadbalancer;

import com.smartproxy.loadbalancer.strategy.RoundRobinStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory providing pluggable strategy resolution.
 */
@Component
public class LoadBalancerStrategyFactory {

    private final Map<LoadBalancerStrategyType, LoadBalancerStrategy> strategyMap = new EnumMap<>(LoadBalancerStrategyType.class);
    private final LoadBalancerStrategy defaultStrategy;

    public LoadBalancerStrategyFactory(List<LoadBalancerStrategy> strategies) {
        LoadBalancerStrategy fallback = null;
        if (strategies != null) {
            for (LoadBalancerStrategy strategy : strategies) {
                strategyMap.put(strategy.getStrategyType(), strategy);
                if (strategy.getStrategyType() == LoadBalancerStrategyType.ROUND_ROBIN) {
                    fallback = strategy;
                }
            }
        }
        if (fallback != null) {
            this.defaultStrategy = fallback;
        } else if (strategies != null && !strategies.isEmpty()) {
            this.defaultStrategy = strategies.get(0);
        } else {
            this.defaultStrategy = new RoundRobinStrategy();
        }
    }

    public LoadBalancerStrategy getStrategy(LoadBalancerStrategyType type) {
        if (type == null) {
            return defaultStrategy;
        }
        return strategyMap.getOrDefault(type, defaultStrategy);
    }

    public LoadBalancerStrategy getStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return defaultStrategy;
        }
        try {
            LoadBalancerStrategyType type = LoadBalancerStrategyType.valueOf(strategyName.trim().toUpperCase());
            return getStrategy(type);
        } catch (IllegalArgumentException e) {
            return defaultStrategy;
        }
    }
}
