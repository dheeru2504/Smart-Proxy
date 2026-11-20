package com.smartproxy.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Service for SHA-256 hashing and in-memory validation of API Keys.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiClientRegistry registry;

    public ApiKeyService(ApiClientRegistry registry) {
        this.registry = registry;
    }

    /**
     * Validates a raw API key against in-memory hashed client cache.
     *
     * @param rawApiKey incoming plaintext API key
     * @return Optional of ClientIdentity if valid and active, empty otherwise
     */
    public Optional<ClientIdentity> validateAndExtractIdentity(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashKey(rawApiKey.trim());
        ApiClientRegistry.CachedApiClient client = registry.findByKeyHash(keyHash);

        if (client == null) {
            log.debug("API key not found for hash: {}", keyHash);
            return Optional.empty();
        }

        if (!client.isActive()) {
            log.debug("API client '{}' is inactive or expired", client.clientId());
            return Optional.empty();
        }

        ClientIdentity identity = ClientIdentity.authenticated(
                client.clientId(),
                client.tenantId(),
                client.roles(),
                client.scopes(),
                AuthType.API_KEY,
                Map.of("keyHash", keyHash)
        );

        return Optional.of(identity);
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }
}
