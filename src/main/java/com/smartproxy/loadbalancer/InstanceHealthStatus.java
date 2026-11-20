package com.smartproxy.loadbalancer;

/**
 * Health states for upstream backend instances.
 */
public enum InstanceHealthStatus {
    UP,
    SUSPECT,
    DOWN,
    DRAINING;

    public boolean isAvailable() {
        return this == UP;
    }
}
