package com.smartproxy.core.auth;

import com.smartproxy.config.SmartProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFilterTest {

    private JwtTokenService jwtTokenService;
    private ApiClientRegistry apiClientRegistry;
    private ApiKeyService apiKeyService;
    private AuthenticationFilter filter;
    private SmartProxyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SmartProxyProperties();
        jwtTokenService = new JwtTokenService(
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256!",
                3600000L
        );
        apiClientRegistry = new ApiClientRegistry(null);
        apiKeyService = new ApiKeyService(apiClientRegistry);
        filter = new AuthenticationFilter(jwtTokenService, apiKeyService, properties);
    }

    @Test
    void shouldStripSpoofedIdentityHeadersAndAuthenticateViaJwt() {
        String token = jwtTokenService.generateToken(
                "real-user-456",
                "real-tenant",
                Set.of("CUSTOMER"),
                Set.of("read"),
                3600000L
        );

        // Attacker attempts to spoof admin identity headers
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resource")
                .header("Authorization", "Bearer " + token)
                .header("X-Client-Id", "spoofed-root-admin")
                .header("X-Tenant-Id", "spoofed-master-tenant")
                .header("X-User-Roles", "SUPER_ADMIN")
                .header("X-Authenticated-Client", "spoofed-client")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ClientIdentity> capturedIdentity = new AtomicReference<>();
        WebFilterChain chain = mutatedExchange -> {
            // Assert that downstream request headers had spoofed headers stripped
            assertFalse(mutatedExchange.getRequest().getHeaders().containsKey("X-Client-Id"));
            assertFalse(mutatedExchange.getRequest().getHeaders().containsKey("X-Tenant-Id"));
            assertFalse(mutatedExchange.getRequest().getHeaders().containsKey("X-User-Roles"));
            assertFalse(mutatedExchange.getRequest().getHeaders().containsKey("X-Authenticated-Client"));

            capturedIdentity.set(mutatedExchange.getAttribute(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ClientIdentity identity = capturedIdentity.get();
        assertNotNull(identity);
        assertTrue(identity.authenticated());
        assertEquals("real-user-456", identity.clientId());
        assertEquals("real-tenant", identity.tenantId());
        assertEquals(AuthType.JWT, identity.authType());
    }

    @Test
    void shouldAuthenticateViaApiKeyHeader() {
        String rawKey = "test_api_key_valid_999";
        String keyHash = ApiKeyService.hashKey(rawKey);

        apiClientRegistry.put(new ApiClientRegistry.CachedApiClient(
                "service-client-1",
                "fintech-tenant",
                keyHash,
                Set.of("SERVICE"),
                Set.of("write:payments"),
                "ACTIVE",
                Instant.now().plus(10, ChronoUnit.DAYS)
        ));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/payments")
                .header("X-API-Key", rawKey)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ClientIdentity> capturedIdentity = new AtomicReference<>();
        WebFilterChain chain = mutatedExchange -> {
            capturedIdentity.set(mutatedExchange.getAttribute(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ClientIdentity identity = capturedIdentity.get();
        assertNotNull(identity);
        assertTrue(identity.authenticated());
        assertEquals("service-client-1", identity.clientId());
        assertEquals("fintech-tenant", identity.tenantId());
        assertEquals(AuthType.API_KEY, identity.authType());
    }

    @Test
    void shouldFallbackToAnonymousWhenNoCredentialsProvided() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/public/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ClientIdentity> capturedIdentity = new AtomicReference<>();
        WebFilterChain chain = mutatedExchange -> {
            capturedIdentity.set(mutatedExchange.getAttribute(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ClientIdentity identity = capturedIdentity.get();
        assertNotNull(identity);
        assertFalse(identity.authenticated());
        assertEquals(AuthType.ANONYMOUS, identity.authType());
    }
}
