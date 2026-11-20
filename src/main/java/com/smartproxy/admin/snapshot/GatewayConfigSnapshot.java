package com.smartproxy.admin.snapshot;

import com.smartproxy.core.auth.ApiClientRegistry;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.ratelimiter.RateLimitPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the gateway's active runtime configuration.
 */
public record GatewayConfigSnapshot(
        long version,
        Instant publishedAt,
        Instant appliedAt,
        List<RouteDefinition> routes,
        Map<String, List<UpstreamInstance>> serviceInstances,
        Map<String, RateLimitPolicy> rateLimitPolicies,
        Map<String, ApiClientRegistry.CachedApiClient> apiClients,
        String checksum
) {
    public GatewayConfigSnapshot {
        routes = routes != null ? List.copyOf(routes) : Collections.emptyList();
        serviceInstances = serviceInstances != null ? Map.copyOf(serviceInstances) : Collections.emptyMap();
        rateLimitPolicies = rateLimitPolicies != null ? Map.copyOf(rateLimitPolicies) : Collections.emptyMap();
        apiClients = apiClients != null ? Map.copyOf(apiClients) : Collections.emptyMap();
    }

    public static GatewayConfigSnapshot initial() {
        return new GatewayConfigSnapshot(
                0L,
                Instant.now(),
                Instant.now(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                "initial-0"
        );
    }

    public GatewayConfigSnapshot withAppliedAt(Instant appliedTimestamp) {
        return new GatewayConfigSnapshot(
                this.version,
                this.publishedAt,
                appliedTimestamp,
                this.routes,
                this.serviceInstances,
                this.rateLimitPolicies,
                this.apiClients,
                this.checksum
        );
    }

    /**
     * Calculates the end-to-end propagation lag in milliseconds between DB publication and gateway swap.
     */
    public long getPropagationLagMs() {
        if (publishedAt == null || appliedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(publishedAt, appliedAt).toMillis());
    }
}
