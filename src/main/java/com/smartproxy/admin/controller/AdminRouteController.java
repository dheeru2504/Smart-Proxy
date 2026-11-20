package com.smartproxy.admin.controller;

import com.smartproxy.admin.dto.RouteRequestDto;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.storage.entity.RateLimitPolicyEntity;
import com.smartproxy.storage.entity.RouteEntity;
import com.smartproxy.storage.entity.ServiceEntity;
import com.smartproxy.storage.repository.RateLimitPolicyRepository;
import com.smartproxy.storage.repository.RouteRepository;
import com.smartproxy.storage.repository.ServiceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/routes")
public class AdminRouteController {

    private final RouteRepository routeRepository;
    private final ServiceRepository serviceRepository;
    private final RateLimitPolicyRepository rateLimitPolicyRepository;
    private final ConfigurationSyncService syncService;

    public AdminRouteController(RouteRepository routeRepository,
                                ServiceRepository serviceRepository,
                                RateLimitPolicyRepository rateLimitPolicyRepository,
                                ConfigurationSyncService syncService) {
        this.routeRepository = routeRepository;
        this.serviceRepository = serviceRepository;
        this.rateLimitPolicyRepository = rateLimitPolicyRepository;
        this.syncService = syncService;
    }

    @GetMapping
    public Mono<List<RouteEntity>> getAllRoutes() {
        return Mono.fromCallable(routeRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<RouteEntity>> getRouteById(@PathVariable UUID id) {
        return Mono.fromCallable(() -> routeRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.map(ResponseEntity::ok)
                        .orElseThrow(() -> GatewayException.notFound("Route not found with ID: " + id)));
    }

    @PostMapping
    public Mono<ResponseEntity<RouteEntity>> createRoute(@Valid @RequestBody RouteRequestDto dto) {
        return Mono.fromCallable(() -> {
            if (routeRepository.existsByRouteId(dto.routeId())) {
                throw GatewayException.badRequest("Route with routeId '" + dto.routeId() + "' already exists.");
            }

            RouteEntity entity = new RouteEntity();
            mapDtoToEntity(dto, entity);

            RouteEntity saved = routeRepository.save(entity);
            syncService.syncRoutesFromDatabase("Admin created route: " + saved.getRouteId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RouteEntity>> updateRoute(@PathVariable UUID id, @Valid @RequestBody RouteRequestDto dto) {
        return Mono.fromCallable(() -> {
            RouteEntity entity = routeRepository.findById(id)
                    .orElseThrow(() -> GatewayException.notFound("Route not found with ID: " + id));

            // Optimistic Locking Check
            if (dto.version() != null && !dto.version().equals(entity.getVersion())) {
                throw new GatewayException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_FAILURE",
                        "Route was modified concurrently. Provided version: " + dto.version() + ", database version: " + entity.getVersion());
            }

            mapDtoToEntity(dto, entity);
            RouteEntity updated = routeRepository.save(entity);
            syncService.syncRoutesFromDatabase("Admin updated route: " + updated.getRouteId());
            return ResponseEntity.ok(updated);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable UUID id) {
        return Mono.fromCallable(() -> {
            RouteEntity entity = routeRepository.findById(id)
                    .orElseThrow(() -> GatewayException.notFound("Route not found with ID: " + id));

            routeRepository.delete(entity);
            syncService.syncRoutesFromDatabase("Admin deleted route: " + entity.getRouteId());
            return ResponseEntity.noContent().<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void mapDtoToEntity(RouteRequestDto dto, RouteEntity entity) {
        entity.setRouteId(dto.routeId());
        entity.setPathPattern(dto.pathPattern());
        entity.setMethods(dto.methods() != null ? dto.methods() : "*");
        entity.setStripPrefixCount(dto.stripPrefixCount());
        entity.setAuthRequired(dto.authRequired());
        entity.setConnectTimeoutMs(dto.connectTimeoutMs() > 0 ? dto.connectTimeoutMs() : 3000);
        entity.setResponseTimeoutMs(dto.responseTimeoutMs() > 0 ? dto.responseTimeoutMs() : 10000);
        entity.setPriority(dto.priority());
        entity.setEnabled(dto.enabled());

        if (dto.serviceId() != null && !dto.serviceId().isBlank()) {
            ServiceEntity service = serviceRepository.findByServiceId(dto.serviceId())
                    .orElse(null);
            entity.setService(service);
        } else {
            entity.setService(null);
        }

        if (dto.rateLimitPolicyName() != null && !dto.rateLimitPolicyName().isBlank()) {
            RateLimitPolicyEntity policy = rateLimitPolicyRepository.findByPolicyName(dto.rateLimitPolicyName())
                    .orElse(null);
            entity.setRateLimitPolicy(policy);
        } else {
            entity.setRateLimitPolicy(null);
        }
    }
}
