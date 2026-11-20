package com.smartproxy.admin.controller;

import com.smartproxy.admin.dto.ServiceInstanceRequestDto;
import com.smartproxy.admin.dto.ServiceRequestDto;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.storage.entity.ServiceEntity;
import com.smartproxy.storage.entity.ServiceInstanceEntity;
import com.smartproxy.storage.repository.ServiceInstanceRepository;
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
@RequestMapping("/api/admin/services")
public class AdminServiceController {

    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository instanceRepository;
    private final ConfigurationSyncService syncService;

    public AdminServiceController(ServiceRepository serviceRepository,
                                  ServiceInstanceRepository instanceRepository,
                                  ConfigurationSyncService syncService) {
        this.serviceRepository = serviceRepository;
        this.instanceRepository = instanceRepository;
        this.syncService = syncService;
    }

    @GetMapping
    public Mono<List<ServiceEntity>> getAllServices() {
        return Mono.fromCallable(serviceRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ServiceEntity>> getServiceById(@PathVariable UUID id) {
        return Mono.fromCallable(() -> serviceRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.map(ResponseEntity::ok)
                        .orElseThrow(() -> GatewayException.notFound("Service not found with ID: " + id)));
    }

    @PostMapping
    public Mono<ResponseEntity<ServiceEntity>> createService(@Valid @RequestBody ServiceRequestDto dto) {
        return Mono.fromCallable(() -> {
            if (serviceRepository.existsByServiceId(dto.serviceId())) {
                throw GatewayException.badRequest("Service with serviceId '" + dto.serviceId() + "' already exists.");
            }

            ServiceEntity entity = new ServiceEntity();
            entity.setServiceId(dto.serviceId());
            entity.setName(dto.name());
            entity.setLoadBalancerStrategy(dto.loadBalancerStrategy());
            entity.setConnectTimeoutMs(dto.connectTimeoutMs());
            entity.setReadTimeoutMs(dto.readTimeoutMs());
            entity.setHealthCheckPath(dto.healthCheckPath());
            entity.setHealthCheckIntervalSec(dto.healthCheckIntervalSec());
            entity.setUnhealthyThreshold(dto.unhealthyThreshold());
            entity.setHealthyThreshold(dto.healthyThreshold());

            ServiceEntity saved = serviceRepository.save(entity);
            syncService.syncRoutesFromDatabase("Admin created service: " + saved.getServiceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ServiceEntity>> updateService(@PathVariable UUID id, @Valid @RequestBody ServiceRequestDto dto) {
        return Mono.fromCallable(() -> {
            ServiceEntity entity = serviceRepository.findById(id)
                    .orElseThrow(() -> GatewayException.notFound("Service not found with ID: " + id));

            if (dto.version() != null && !dto.version().equals(entity.getVersion())) {
                throw new GatewayException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_FAILURE",
                        "Service modified concurrently. Provided version: " + dto.version() + ", DB version: " + entity.getVersion());
            }

            entity.setServiceId(dto.serviceId());
            entity.setName(dto.name());
            entity.setLoadBalancerStrategy(dto.loadBalancerStrategy());
            entity.setConnectTimeoutMs(dto.connectTimeoutMs());
            entity.setReadTimeoutMs(dto.readTimeoutMs());
            entity.setHealthCheckPath(dto.healthCheckPath());
            entity.setHealthCheckIntervalSec(dto.healthCheckIntervalSec());
            entity.setUnhealthyThreshold(dto.unhealthyThreshold());
            entity.setHealthyThreshold(dto.healthyThreshold());

            ServiceEntity updated = serviceRepository.save(entity);
            syncService.syncRoutesFromDatabase("Admin updated service: " + updated.getServiceId());
            return ResponseEntity.ok(updated);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{serviceId}/instances")
    public Mono<ResponseEntity<ServiceInstanceEntity>> addInstance(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceInstanceRequestDto dto) {
        return Mono.fromCallable(() -> {
            ServiceEntity service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> GatewayException.notFound("Service not found with ID: " + serviceId));

            ServiceInstanceEntity instance = new ServiceInstanceEntity();
            instance.setService(service);
            instance.setHost(dto.host());
            instance.setPort(dto.port());
            instance.setWeight(dto.weight() > 0 ? dto.weight() : 1);
            instance.setStatus(dto.status() != null ? dto.status() : "HEALTHY");

            ServiceInstanceEntity saved = instanceRepository.save(instance);
            syncService.syncRoutesFromDatabase("Admin added instance to service: " + service.getServiceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
