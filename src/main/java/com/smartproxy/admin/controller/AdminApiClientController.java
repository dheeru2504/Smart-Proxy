package com.smartproxy.admin.controller;

import com.smartproxy.admin.dto.ApiClientRequestDto;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.storage.entity.ApiClientEntity;
import com.smartproxy.storage.entity.RateLimitPolicyEntity;
import com.smartproxy.storage.repository.ApiClientRepository;
import com.smartproxy.storage.repository.RateLimitPolicyRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/api-clients")
public class AdminApiClientController {

    private final ApiClientRepository repository;
    private final RateLimitPolicyRepository rateLimitPolicyRepository;
    private final ConfigurationSyncService syncService;
    private final com.smartproxy.core.auth.ApiClientRegistry apiClientRegistry;

    public AdminApiClientController(ApiClientRepository repository,
                                    RateLimitPolicyRepository rateLimitPolicyRepository,
                                    ConfigurationSyncService syncService,
                                    com.smartproxy.core.auth.ApiClientRegistry apiClientRegistry) {
        this.repository = repository;
        this.rateLimitPolicyRepository = rateLimitPolicyRepository;
        this.syncService = syncService;
        this.apiClientRegistry = apiClientRegistry;
    }

    @GetMapping
    public Mono<List<ApiClientEntity>> getAllClients() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<ApiClientEntity>> createClient(@Valid @RequestBody ApiClientRequestDto dto) {
        return Mono.fromCallable(() -> {
            if (repository.existsByClientId(dto.clientId())) {
                throw GatewayException.badRequest("API client with clientId '" + dto.clientId() + "' already exists.");
            }

            String keyPrefix = dto.rawApiKey().length() >= 8 ? dto.rawApiKey().substring(0, 8) : dto.rawApiKey();
            String keyHash = hashApiKey(dto.rawApiKey());

            ApiClientEntity entity = new ApiClientEntity();
            entity.setClientId(dto.clientId());
            entity.setClientName(dto.clientName());
            entity.setKeyPrefix(keyPrefix);
            entity.setKeyHash(keyHash);
            entity.setRole(dto.role() != null ? dto.role() : "STANDARD_CONSUMER");
            entity.setEnabled(dto.enabled());
            entity.setExpiresAt(dto.expiresAt());

            if (dto.rateLimitPolicyName() != null && !dto.rateLimitPolicyName().isBlank()) {
                RateLimitPolicyEntity policy = rateLimitPolicyRepository.findByPolicyName(dto.rateLimitPolicyName())
                        .orElse(null);
                entity.setRateLimitPolicy(policy);
            }

            ApiClientEntity saved = repository.save(entity);
            apiClientRegistry.putEntity(saved);
            syncService.bumpVersion("API_CLIENTS", "Admin created client: " + saved.getClientId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encode(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
