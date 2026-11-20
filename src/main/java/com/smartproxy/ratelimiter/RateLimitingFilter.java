package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.core.auth.AuthenticationFilter;
import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway WebFilter applying distributed rate limiting policies and RFC header injection.
 * Integrates single-policy algorithms and hierarchical multi-tier rate limiting.
 */
@Component
public class RateLimitingFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    public static final String HEADER_RATELIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RATELIMIT_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterFactory limiterFactory;
    private final RateLimitKeyResolver keyResolver;
    private final MultiTierPolicyResolver multiTierPolicyResolver;
    private final MultiTierRateLimiter multiTierRateLimiter;
    private final SmartProxyProperties properties;

    @Autowired
    public RateLimitingFilter(RateLimiterFactory limiterFactory,
                              RateLimitKeyResolver keyResolver,
                              MultiTierPolicyResolver multiTierPolicyResolver,
                              MultiTierRateLimiter multiTierRateLimiter,
                              SmartProxyProperties properties) {
        this.limiterFactory = limiterFactory;
        this.keyResolver = keyResolver;
        this.multiTierPolicyResolver = multiTierPolicyResolver;
        this.multiTierRateLimiter = multiTierRateLimiter;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        // Runs after RouteAuthorizationFilter and before ReverseProxyFilter
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getRatelimiter().isEnabled()) {
            return chain.filter(exchange);
        }

        RouteDefinition route = exchange.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        ClientIdentity identity = exchange.getAttributeOrDefault(
                AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR,
                ClientIdentity.anonymous("127.0.0.1")
        );

        // Resolve hierarchical multi-tier policies (Global, Tenant, Client, User, Route)
        List<RedisMultiTierRateLimiter.TierRequest> tiers =
                multiTierPolicyResolver.resolveTiers(exchange.getRequest(), route, identity);

        return multiTierRateLimiter.isAllowed(tiers)
                .flatMap(result -> {
                    ServerHttpResponse response = exchange.getResponse();

                    // Inject standard rate limit headers
                    response.getHeaders().set(HEADER_RATELIMIT_LIMIT, String.valueOf(result.limit()));
                    response.getHeaders().set(HEADER_RATELIMIT_REMAINING, String.valueOf(result.remainingTokens()));
                    response.getHeaders().set(HEADER_RATELIMIT_RESET, String.valueOf(result.ttlSeconds()));

                    if (!result.allowed()) {
                        response.getHeaders().set(HEADER_RETRY_AFTER, String.valueOf(result.ttlSeconds()));
                        log.warn("Multi-tier rate limit exceeded for client '{}' on route '{}'. Remaining TTL: {}s",
                                identity.clientId(), route.getRouteId(), result.ttlSeconds());
                        return Mono.error(GatewayException.rateLimitExceeded(
                                "Too Many Requests. Rate limit quota of " + result.limit() + " exceeded."
                        ));
                    }

                    return chain.filter(exchange);
                });
    }
}
