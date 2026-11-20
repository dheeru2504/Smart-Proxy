package com.smartproxy.loadbalancer;

public enum LoadBalancerStrategyType {
    ROUND_ROBIN,
    WEIGHTED_ROUND_ROBIN,
    LEAST_CONNECTIONS,
    POWER_OF_TWO_CHOICES,
    IP_HASH,
    RANDOM
}
