package com.smartproxy.core.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable/configurable model representing a dynamic Gateway route definition.
 */
public class RouteDefinition {

    private String routeId;
    private String pathPattern;
    private Set<String> methods = new HashSet<>();
    private String serviceId;
    private UUID clusterId;
    private int stripPrefixCount = 0;
    private boolean authRequired = false;
    private int connectTimeoutMs = 3000;
    private int responseTimeoutMs = 10000;
    private String rateLimitPolicyName;
    private UUID rateLimitPolicyId;
    private String circuitBreakerConfigName;
    private UUID circuitBreakerConfigId;
    private int order = 0;
    private boolean enabled = true;

    public RouteDefinition() {}

    public RouteDefinition(String routeId, String pathPattern, Set<String> methods,
                           String serviceId, UUID clusterId, int stripPrefixCount,
                           boolean authRequired, int connectTimeoutMs, int responseTimeoutMs,
                           String rateLimitPolicyName, UUID rateLimitPolicyId,
                           String circuitBreakerConfigName, UUID circuitBreakerConfigId,
                           int order, boolean enabled) {
        this.routeId = routeId;
        this.pathPattern = pathPattern;
        this.methods = methods != null ? new HashSet<>(methods) : new HashSet<>();
        this.serviceId = serviceId;
        this.clusterId = clusterId;
        this.stripPrefixCount = stripPrefixCount;
        this.authRequired = authRequired;
        this.connectTimeoutMs = connectTimeoutMs;
        this.responseTimeoutMs = responseTimeoutMs;
        this.rateLimitPolicyName = rateLimitPolicyName;
        this.rateLimitPolicyId = rateLimitPolicyId;
        this.circuitBreakerConfigName = circuitBreakerConfigName;
        this.circuitBreakerConfigId = circuitBreakerConfigId;
        this.order = order;
        this.enabled = enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    public Set<String> getMethods() { return methods != null ? Collections.unmodifiableSet(methods) : Collections.emptySet(); }
    public void setMethods(Set<String> methods) { this.methods = methods != null ? new HashSet<>(methods) : new HashSet<>(); }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public UUID getClusterId() { return clusterId; }
    public void setClusterId(UUID clusterId) { this.clusterId = clusterId; }

    public int getStripPrefixCount() { return stripPrefixCount; }
    public void setStripPrefixCount(int stripPrefixCount) { this.stripPrefixCount = stripPrefixCount; }

    public boolean isAuthRequired() { return authRequired; }
    public void setAuthRequired(boolean authRequired) { this.authRequired = authRequired; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public void setResponseTimeoutMs(int responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }

    public String getRateLimitPolicyName() { return rateLimitPolicyName; }
    public void setRateLimitPolicyName(String rateLimitPolicyName) { this.rateLimitPolicyName = rateLimitPolicyName; }

    public UUID getRateLimitPolicyId() { return rateLimitPolicyId; }
    public void setRateLimitPolicyId(UUID rateLimitPolicyId) { this.rateLimitPolicyId = rateLimitPolicyId; }

    public String getCircuitBreakerConfigName() { return circuitBreakerConfigName; }
    public void setCircuitBreakerConfigName(String circuitBreakerConfigName) { this.circuitBreakerConfigName = circuitBreakerConfigName; }

    public UUID getCircuitBreakerConfigId() { return circuitBreakerConfigId; }
    public void setCircuitBreakerConfigId(UUID circuitBreakerConfigId) { this.circuitBreakerConfigId = circuitBreakerConfigId; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static class Builder {
        private String routeId;
        private String pathPattern;
        private Set<String> methods = new HashSet<>();
        private String serviceId;
        private UUID clusterId;
        private int stripPrefixCount = 0;
        private boolean authRequired = false;
        private int connectTimeoutMs = 3000;
        private int responseTimeoutMs = 10000;
        private String rateLimitPolicyName;
        private UUID rateLimitPolicyId;
        private String circuitBreakerConfigName;
        private UUID circuitBreakerConfigId;
        private int order = 0;
        private boolean enabled = true;

        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder pathPattern(String pathPattern) { this.pathPattern = pathPattern; return this; }
        public Builder methods(Set<String> methods) { this.methods = methods; return this; }
        public Builder method(String method) { if (method != null) this.methods.add(method.toUpperCase()); return this; }
        public Builder serviceId(String serviceId) { this.serviceId = serviceId; return this; }
        public Builder clusterId(UUID clusterId) { this.clusterId = clusterId; return this; }
        public Builder stripPrefixCount(int count) { this.stripPrefixCount = count; return this; }
        public Builder authRequired(boolean authRequired) { this.authRequired = authRequired; return this; }
        public Builder connectTimeoutMs(int timeoutMs) { this.connectTimeoutMs = timeoutMs; return this; }
        public Builder responseTimeoutMs(int timeoutMs) { this.responseTimeoutMs = timeoutMs; return this; }
        public Builder rateLimitPolicyName(String name) { this.rateLimitPolicyName = name; return this; }
        public Builder rateLimitPolicyId(UUID id) { this.rateLimitPolicyId = id; return this; }
        public Builder circuitBreakerConfigName(String name) { this.circuitBreakerConfigName = name; return this; }
        public Builder circuitBreakerConfigId(UUID id) { this.circuitBreakerConfigId = id; return this; }
        public Builder order(int order) { this.order = order; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

        public RouteDefinition build() {
            return new RouteDefinition(routeId, pathPattern, methods, serviceId, clusterId,
                    stripPrefixCount, authRequired, connectTimeoutMs, responseTimeoutMs,
                    rateLimitPolicyName, rateLimitPolicyId, circuitBreakerConfigName,
                    circuitBreakerConfigId, order, enabled);
        }
    }
}
