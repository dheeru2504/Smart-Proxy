package com.smartproxy.ratelimiter;

import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.model.RouteDefinition;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * Resolves the rate limit partition key dynamically across multiple dimensions (IP, User, API-Key, Tenant, Route).
 */
@Component
public class RateLimitKeyResolver {

    /**
     * Resolves the partition key based on policy dimension.
     *
     * @param request  incoming HTTP request
     * @param route    matched route definition
     * @param identity validated client identity
     * @param limitBy  dimension string (IP, USER, API_KEY, TENANT, ROUTE, COMPOSITE)
     * @return resolved unique key
     */
    public String resolveKey(ServerHttpRequest request, RouteDefinition route,
                             ClientIdentity identity, String limitBy) {
        String dimension = (limitBy != null && !limitBy.isBlank()) ? limitBy.trim().toUpperCase() : "COMPOSITE";

        return switch (dimension) {
            case "IP" -> resolveClientIp(request);
            case "USER" -> identity != null ? identity.clientId() : resolveClientIp(request);
            case "API_KEY" -> {
                if (identity != null && identity.claims().containsKey("keyHash")) {
                    yield (String) identity.claims().get("keyHash");
                }
                yield identity != null ? identity.clientId() : resolveClientIp(request);
            }
            case "TENANT" -> identity != null ? identity.tenantId() : "default";
            case "ROUTE" -> route != null ? route.getRouteId() : "global";
            default -> {
                String tenant = identity != null ? identity.tenantId() : "default";
                String client = identity != null ? identity.clientId() : resolveClientIp(request);
                String routeId = route != null ? route.getRouteId() : "global";
                yield tenant + ":" + client + ":" + routeId;
            }
        };
    }

    private String resolveClientIp(ServerHttpRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "127.0.0.1";
    }
}
