package com.smartproxy.admin.controller;

import com.smartproxy.admin.dto.RateLimitPolicyRequestDto;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.storage.entity.RateLimitPolicyEntity;
import com.smartproxy.storage.repository.RateLimitPolicyRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rate-limits")
public class AdminRateLimitPolicyController {

    private final RateLimitPolicyRepository repository;
    private final ConfigurationSyncService syncService;

    public AdminRateLimitPolicyController(RateLimitPolicyRepository repository, ConfigurationSyncService syncService) {
        this.repository = repository;
        this.syncService = syncService;
    }

    @GetMapping
    public Mono<List<RateLimitPolicyEntity>> getAllPolicies() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<RateLimitPolicyEntity>> createPolicy(@Valid @RequestBody RateLimitPolicyRequestDto dto) {
        return Mono.fromCallable(() -> {
            if (repository.existsByPolicyName(dto.policyName())) {
                throw GatewayException.badRequest("Rate limit policy with name '" + dto.policyName() + "' already exists.");
            }

            RateLimitPolicyEntity entity = new RateLimitPolicyEntity();
            entity.setPolicyName(dto.policyName());
            entity.setAlgorithm(dto.algorithm());
            entity.setReplenishRate(dto.replenishRate());
            entity.setBurstCapacity(dto.burstCapacity());
            entity.setWindowDurationSec(dto.windowDurationSec());
            entity.setLimitBy(dto.limitBy());

            RateLimitPolicyEntity saved = repository.save(entity);
            syncService.syncRoutesFromDatabase("Admin created policy: " + saved.getPolicyName());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RateLimitPolicyEntity>> updatePolicy(@PathVariable UUID id, @Valid @RequestBody RateLimitPolicyRequestDto dto) {
        return Mono.fromCallable(() -> {
            RateLimitPolicyEntity entity = repository.findById(id)
                    .orElseThrow(() -> GatewayException.notFound("Rate limit policy not found with ID: " + id));

            if (dto.version() != null && !dto.version().equals(entity.getVersion())) {
                throw new GatewayException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_FAILURE",
                        "Policy modified concurrently. Provided version: " + dto.version() + ", DB version: " + entity.getVersion());
            }

            entity.setPolicyName(dto.policyName());
            entity.setAlgorithm(dto.algorithm());
            entity.setReplenishRate(dto.replenishRate());
            entity.setBurstCapacity(dto.burstCapacity());
            entity.setWindowDurationSec(dto.windowDurationSec());
            entity.setLimitBy(dto.limitBy());

            RateLimitPolicyEntity updated = repository.save(entity);
            syncService.syncRoutesFromDatabase("Admin updated policy: " + updated.getPolicyName());
            return ResponseEntity.ok(updated);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
