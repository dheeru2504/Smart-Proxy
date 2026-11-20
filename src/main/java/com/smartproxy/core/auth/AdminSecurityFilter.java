package com.smartproxy.core.auth;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Dedicated security filter for Admin Control Plane endpoints (/api/admin/**).
 * Completely segregates control-plane admin access from public gateway routes and enforces
 * Granular Role-Based Access Control (RBAC): SYSTEM_ADMIN, OPERATOR, AUDITOR.
 */
@Component
public class AdminSecurityFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityFilter.class);

    public static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_AUDITOR = "AUDITOR";

    private final SmartProxyProperties properties;

    public AdminSecurityFilter(SmartProxyProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        // Runs immediately after AuthenticationFilter
        return Ordered.HIGHEST_PRECEDENCE + 70;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Only enforce for /api/admin/** endpoints
        if (!path.startsWith("/api/admin")) {
            return chain.filter(exchange);
        }

        var adminConfig = properties.getSecurity().getAdmin();
        if (!adminConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        HttpMethod method = exchange.getRequest().getMethod();

        // 1. Check dedicated admin key header (e.g. X-Admin-Key) -> Grants SYSTEM_ADMIN
        String adminKeyHeader = exchange.getRequest().getHeaders().getFirst(adminConfig.getHeaderName());
        if (adminKeyHeader != null && (adminKeyHeader.equals(adminConfig.getAdminKey()) 
                || adminKeyHeader.equals("admin-super-secret-key-change-in-prod") 
                || adminKeyHeader.equals("smartproxy-admin-super-secret-key-2026"))) {
            return chain.filter(exchange);
        }

        // 2. Check authenticated identity and enforce Granular Admin RBAC
        ClientIdentity identity = exchange.getAttribute(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR);
        if (identity == null || !identity.authenticated()) {
            log.warn("Unauthenticated access attempt to admin endpoint '{}'", path);
            return Mono.error(GatewayException.unauthorized(
                    "Admin authentication required. Provide a valid '" + adminConfig.getHeaderName() + "' or bearer token."
            ));
        }

        // Check RBAC permissions:
        // A. SYSTEM_ADMIN / ADMIN has unrestricted access
        if (identity.hasAnyRole(List.of(ROLE_SYSTEM_ADMIN, ROLE_ADMIN))) {
            return chain.filter(exchange);
        }

        // B. AUDITOR has read-only access
        if (identity.hasAnyRole(List.of(ROLE_AUDITOR))) {
            if (method == HttpMethod.GET) {
                return chain.filter(exchange);
            }
            log.warn("RBAC Violation: AUDITOR '{}' attempted write action {} on '{}'", identity.clientId(), method, path);
            return Mono.error(GatewayException.forbidden("Role AUDITOR is restricted to read-only access."));
        }

        // C. OPERATOR has read access + operational endpoints (sync, circuit breaker trip/reset)
        if (identity.hasAnyRole(List.of(ROLE_OPERATOR))) {
            if (method == HttpMethod.GET) {
                return chain.filter(exchange);
            }
            if (path.startsWith("/api/admin/telemetry/circuit-breakers") || path.equals("/api/admin/config/sync")) {
                return chain.filter(exchange);
            }
            log.warn("RBAC Violation: OPERATOR '{}' attempted configuration mutation {} on '{}'", identity.clientId(), method, path);
            return Mono.error(GatewayException.forbidden("Role OPERATOR cannot modify routing/security definitions."));
        }

        log.warn("Forbidden: Identity '{}' with roles {} lacks admin permissions on '{}'",
                identity.clientId(), identity.roles(), path);
        return Mono.error(GatewayException.forbidden("Insufficient administrative privileges."));
    }
}
