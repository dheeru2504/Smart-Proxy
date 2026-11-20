package com.smartproxy.core.auth;

import com.smartproxy.config.SmartProxyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;

/**
 * Early gateway filter for resolving client identity and stripping spoofed headers.
 * Never trusts client-supplied identity headers.
 */
@Component
public class AuthenticationFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    public static final String AUTHENTICATED_IDENTITY_ATTR = "smartproxy.client.identity";

    private static final Set<String> SPOOFABLE_IDENTITY_HEADERS = Set.of(
            "x-client-id",
            "x-tenant-id",
            "x-user-roles",
            "x-user-scopes",
            "x-authenticated-client",
            "x-authenticated-tenant",
            "x-authenticated-roles",
            "x-authenticated-scopes",
            "x-authenticated-type",
            "x-user-id"
    );

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;
    private final SmartProxyProperties properties;

    public AuthenticationFilter(JwtTokenService jwtTokenService, ApiKeyService apiKeyService, SmartProxyProperties properties) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        // Runs early in the chain, right after TraceIdFilter
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. Strip spoofable client identity headers by creating sanitized request
        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(httpHeaders -> {
                    for (String header : SPOOFABLE_IDENTITY_HEADERS) {
                        httpHeaders.remove(header);
                    }
                })
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(sanitizedRequest).build();

        // 2. Resolve client identity from credentials
        ClientIdentity identity = resolveIdentity(sanitizedRequest);

        // 3. Attach validated identity to exchange attributes
        mutatedExchange.getAttributes().put(AUTHENTICATED_IDENTITY_ATTR, identity);

        if (identity.authenticated()) {
            log.debug("Authenticated request: client='{}', tenant='{}', authType={}, roles={}",
                    identity.clientId(), identity.tenantId(), identity.authType(), identity.roles());
        }

        return chain.filter(mutatedExchange);
    }

    private ClientIdentity resolveIdentity(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        // Check Authorization header
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && !authHeader.isBlank()) {
            String trimmed = authHeader.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = trimmed.substring(7).trim();
                Optional<ClientIdentity> jwtIdentity = jwtTokenService.validateAndExtractIdentity(token);
                if (jwtIdentity.isPresent()) {
                    return jwtIdentity.get();
                }
            } else if (trimmed.regionMatches(true, 0, "ApiKey ", 0, 7)) {
                String key = trimmed.substring(7).trim();
                Optional<ClientIdentity> apiKeyIdentity = apiKeyService.validateAndExtractIdentity(key);
                if (apiKeyIdentity.isPresent()) {
                    return apiKeyIdentity.get();
                }
            }
        }

        // Check custom API Key header (e.g. X-API-Key)
        String apiKeyHeaderName = properties.getSecurity().getApiKey().getHeaderName();
        String apiKeyValue = headers.getFirst(apiKeyHeaderName);
        if (apiKeyValue != null && !apiKeyValue.isBlank()) {
            Optional<ClientIdentity> apiKeyIdentity = apiKeyService.validateAndExtractIdentity(apiKeyValue.trim());
            if (apiKeyIdentity.isPresent()) {
                return apiKeyIdentity.get();
            }
        }

        // Fallback to anonymous identity
        String clientIp = resolveClientIp(request);
        return ClientIdentity.anonymous(clientIp);
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "127.0.0.1";
    }
}
