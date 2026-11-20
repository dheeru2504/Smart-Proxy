package com.smartproxy.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceTest {

    private ApiClientRegistry registry;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        registry = new ApiClientRegistry(null);
        apiKeyService = new ApiKeyService(registry);
    }

    @Test
    void shouldValidateHashedApiKeySuccessfully() {
        String rawKey = "sp_live_secret_key_12345";
        String keyHash = ApiKeyService.hashKey(rawKey);

        registry.put(
                new ApiClientRegistry.CachedApiClient(
                        "payment-service",
                        "tenant-fintech",
                        keyHash,
                        Set.of("SERVICE", "PAYMENT_WRITER"),
                        Set.of("payments:create", "payments:read"),
                        "ACTIVE",
                        Instant.now().plus(30, ChronoUnit.DAYS)
                )
        );

        Optional<ClientIdentity> identityOpt = apiKeyService.validateAndExtractIdentity(rawKey);
        assertTrue(identityOpt.isPresent());

        ClientIdentity identity = identityOpt.get();
        assertEquals("payment-service", identity.clientId());
        assertEquals("tenant-fintech", identity.tenantId());
        assertEquals(AuthType.API_KEY, identity.authType());
        assertTrue(identity.authenticated());
        assertTrue(identity.hasRole("PAYMENT_WRITER"));
        assertTrue(identity.hasScope("payments:create"));
    }

    @Test
    void shouldRejectExpiredApiKey() {
        String rawKey = "sp_expired_key";
        String keyHash = ApiKeyService.hashKey(rawKey);

        registry.put(
                new ApiClientRegistry.CachedApiClient(
                        "expired-client",
                        "default",
                        keyHash,
                        Set.of("USER"),
                        Set.of("read"),
                        "ACTIVE",
                        Instant.now().minus(1, ChronoUnit.DAYS)
                )
        );

        Optional<ClientIdentity> identityOpt = apiKeyService.validateAndExtractIdentity(rawKey);
        assertTrue(identityOpt.isEmpty(), "Expired API key must be rejected");
    }

    @Test
    void shouldRejectInactiveApiKey() {
        String rawKey = "sp_revoked_key";
        String keyHash = ApiKeyService.hashKey(rawKey);

        registry.put(
                new ApiClientRegistry.CachedApiClient(
                        "revoked-client",
                        "default",
                        keyHash,
                        Set.of("USER"),
                        Set.of("read"),
                        "REVOKED",
                        Instant.now().plus(1, ChronoUnit.DAYS)
                )
        );

        Optional<ClientIdentity> identityOpt = apiKeyService.validateAndExtractIdentity(rawKey);
        assertTrue(identityOpt.isEmpty(), "Revoked/Inactive API key must be rejected");
    }
}
