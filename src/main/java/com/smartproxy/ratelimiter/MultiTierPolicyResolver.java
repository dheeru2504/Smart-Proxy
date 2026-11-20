package com.smartproxy.ratelimiter;

import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.model.RouteDefinition;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves hierarchical multi-tier rate limiting policies (Global, Tenant, API-Key, User, Route).
 */
@Component
public class MultiTierPolicyResolver {

    /**
     * Resolves all applicable rate-limit tiers for the given request context.
     */
    public List<RedisMultiTierRateLimiter.TierRequest> resolveTiers(ServerHttpRequest request,
                                                                    RouteDefinition route,
                                                                    ClientIdentity identity) {
        List<RedisMultiTierRateLimiter.TierRequest> tiers = new ArrayList<>();

        // 1. Global Tier (DDoS & cluster capacity protection)
        tiers.add(new RedisMultiTierRateLimiter.TierRequest("global", 10000, 10000, 1));

        // 2. Tenant Tier (Tenant tier quotas)
        String tenantId = (identity != null && identity.tenantId() != null) ? identity.tenantId() : "default";
        tiers.add(new RedisMultiTierRateLimiter.TierRequest("tenant:" + tenantId, 1000, 500, 1));

        // 3. API-Key / Client Tier (Client-level quotas)
        if (identity != null && identity.authenticated()) {
            tiers.add(new RedisMultiTierRateLimiter.TierRequest("client:" + identity.clientId(), 200, 100, 1));
        }

        // 4. User Tier (Per-user quota)
        if (identity != null && identity.authenticated() && identity.hasRole("USER")) {
            tiers.add(new RedisMultiTierRateLimiter.TierRequest("user:" + identity.clientId(), 50, 20, 1));
        }

        // 5. Route Tier (Endpoint cost & resource protection)
        if (route != null) {
            tiers.add(new RedisMultiTierRateLimiter.TierRequest("route:" + route.getRouteId(), 100, 50, 1));
        }

        return tiers;
    }
}
