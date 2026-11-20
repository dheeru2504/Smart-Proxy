package com.smartproxy.core.auth;

import com.smartproxy.storage.entity.ApiClientEntity;
import com.smartproxy.storage.repository.ApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for API Client credentials and hashed keys.
 * Ensures zero database queries on public ingress request paths.
 */
@Component
public class ApiClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiClientRegistry.class);

    public record CachedApiClient(
            String clientId,
            String tenantId,
            String keyHash,
            Set<String> roles,
            Set<String> scopes,
            String status,
            Instant expiresAt
    ) {
        public boolean isActive() {
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                return false;
            }
            return expiresAt == null || expiresAt.isAfter(Instant.now());
        }
    }

    private final Map<String, CachedApiClient> clientsByKeyHash = new ConcurrentHashMap<>();
    private final ApiClientRepository apiClientRepository;

    public ApiClientRegistry(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Hydrating API clients in-memory cache from database...");
        reloadAllFromDatabase();
    }

    public synchronized void reloadAllFromDatabase() {
        if (apiClientRepository == null) return;
        try {
            List<ApiClientEntity> entities = apiClientRepository.findAll();
            clientsByKeyHash.clear();
            for (ApiClientEntity entity : entities) {
                putEntity(entity);
            }
            log.info("Hydrated {} API clients into in-memory security cache.", clientsByKeyHash.size());
        } catch (Exception e) {
            log.warn("Could not hydrate API clients from database during startup: {}", e.getMessage());
        }
    }

    public void putEntity(ApiClientEntity entity) {
        if (entity == null || entity.getKeyHash() == null) return;
        Set<String> roles = entity.getRole() != null ? Set.of(entity.getRole()) : Set.of("STANDARD_CONSUMER");
        Set<String> scopes = Set.of("api:access");

        CachedApiClient cached = new CachedApiClient(
                entity.getClientId(),
                "default",
                entity.getKeyHash(),
                roles,
                scopes,
                entity.isEnabled() ? "ACTIVE" : "INACTIVE",
                entity.getExpiresAt()
        );
        clientsByKeyHash.put(entity.getKeyHash(), cached);
    }

    public void put(CachedApiClient client) {
        if (client != null && client.keyHash() != null) {
            clientsByKeyHash.put(client.keyHash(), client);
        }
    }

    public CachedApiClient findByKeyHash(String keyHash) {
        if (keyHash == null) return null;
        return clientsByKeyHash.get(keyHash);
    }

    public void removeByKeyHash(String keyHash) {
        if (keyHash != null) {
            clientsByKeyHash.remove(keyHash);
        }
    }

    public int size() {
        return clientsByKeyHash.size();
    }

    private static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(HashSet::new, Set::add, Set::addAll);
    }
}
