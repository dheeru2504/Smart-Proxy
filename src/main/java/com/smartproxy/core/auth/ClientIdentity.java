package com.smartproxy.core.auth;

import java.util.*;

/**
 * Immutable representation of a validated caller identity in the Smart-Proxy ecosystem.
 */
public record ClientIdentity(
        String clientId,
        String tenantId,
        Set<String> roles,
        Set<String> scopes,
        AuthType authType,
        boolean authenticated,
        Map<String, Object> claims
) {

    public ClientIdentity {
        roles = roles != null ? Collections.unmodifiableSet(new HashSet<>(roles)) : Collections.emptySet();
        scopes = scopes != null ? Collections.unmodifiableSet(new HashSet<>(scopes)) : Collections.emptySet();
        claims = claims != null ? Collections.unmodifiableMap(new HashMap<>(claims)) : Collections.emptyMap();
    }

    public static ClientIdentity anonymous(String clientIp) {
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        return new ClientIdentity("anonymous-" + ip, "default", Set.of("ANONYMOUS"), Set.of("public"),
                AuthType.ANONYMOUS, false, Map.of("ip", ip));
    }

    public static ClientIdentity authenticated(String clientId, String tenantId, Set<String> roles,
                                              Set<String> scopes, AuthType authType, Map<String, Object> claims) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        return new ClientIdentity(clientId, effectiveTenant, roles, scopes, authType, true, claims);
    }

    public static ClientIdentity admin(String adminUser) {
        return new ClientIdentity(adminUser, "system", Set.of("ADMIN", "SYSTEM_ADMIN"),
                Set.of("admin:read", "admin:write", "admin:all"), AuthType.ADMIN, true, Map.of());
    }

    public boolean hasRole(String role) {
        if (role == null) return false;
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    public boolean hasAnyRole(Collection<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) return true;
        return requiredRoles.stream().anyMatch(this::hasRole);
    }

    public boolean hasScope(String scope) {
        if (scope == null) return false;
        return scopes.stream().anyMatch(s -> s.equalsIgnoreCase(scope));
    }

    public boolean hasAnyScope(Collection<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) return true;
        return requiredScopes.stream().anyMatch(this::hasScope);
    }

    /**
     * Resolves unique tenant-aware rate limiter key.
     */
    public String getRateLimitKey() {
        if (!authenticated) {
            return "anon:" + (claims.containsKey("ip") ? claims.get("ip") : clientId);
        }
        return tenantId + ":" + clientId;
    }
}
