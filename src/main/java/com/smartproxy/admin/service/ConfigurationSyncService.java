package com.smartproxy.admin.service;

import com.smartproxy.admin.snapshot.ConfigEventPublisher;
import com.smartproxy.admin.snapshot.ConfigUpdateEvent;
import com.smartproxy.admin.snapshot.GatewayConfigSnapshot;
import com.smartproxy.admin.snapshot.GatewaySnapshotManager;
import com.smartproxy.admin.snapshot.SnapshotValidator;
import com.smartproxy.core.auth.ApiClientRegistry;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.loadbalancer.InstanceHealthStatus;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.storage.entity.ConfigurationVersionEntity;
import com.smartproxy.storage.entity.RouteEntity;
import com.smartproxy.storage.entity.ServiceEntity;
import com.smartproxy.storage.entity.ServiceInstanceEntity;
import com.smartproxy.storage.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for synchronizing database configurations into in-memory snapshots.
 * Ensures PostgreSQL is authoritative and coordinates atomic snapshot swapping.
 */
@Service
public class ConfigurationSyncService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationSyncService.class);

    private final RouteRepository routeRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ConfigurationVersionRepository versionRepository;
    private final GatewaySnapshotManager snapshotManager;
    private final ConfigEventPublisher eventPublisher;

    public ConfigurationSyncService(RouteRepository routeRepository,
                                    ServiceRepository serviceRepository,
                                    ServiceInstanceRepository serviceInstanceRepository,
                                    ConfigurationVersionRepository versionRepository,
                                    GatewaySnapshotManager snapshotManager,
                                    ConfigEventPublisher eventPublisher) {
        this.routeRepository = routeRepository;
        this.serviceRepository = serviceRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.versionRepository = versionRepository;
        this.snapshotManager = snapshotManager;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Bootstrapping in-memory routing snapshot from PostgreSQL...");
        syncRoutesFromDatabase("Application startup bootstrap");
    }

    /**
     * Pulls full authoritative state from PostgreSQL, validates, atomically swaps snapshot,
     * and broadcasts CONFIG_UPDATED to RabbitMQ.
     */
    @Transactional
    public synchronized long syncRoutesFromDatabase(String reason) {
        Instant publishedAt = Instant.now();

        // 1. Load routes
        List<RouteEntity> entities = routeRepository.findAll();
        List<RouteDefinition> definitions = entities.stream()
                .filter(RouteEntity::isEnabled)
                .map(this::toRouteDefinition)
                .toList();

        // 2. Load service instances
        Map<String, List<UpstreamInstance>> serviceInstances = loadServiceInstances();

        // 3. Determine next version
        long nextVersion = bumpVersion("GLOBAL", reason);

        // 4. Build candidate snapshot and compute SHA-256 checksum
        GatewayConfigSnapshot draftSnapshot = new GatewayConfigSnapshot(
                nextVersion,
                publishedAt,
                null,
                definitions,
                serviceInstances,
                Collections.emptyMap(),
                Collections.emptyMap(),
                null
        );
        String checksum = SnapshotValidator.computeChecksum(draftSnapshot);
        GatewayConfigSnapshot candidateSnapshot = new GatewayConfigSnapshot(
                nextVersion,
                publishedAt,
                null,
                definitions,
                serviceInstances,
                Collections.emptyMap(),
                Collections.emptyMap(),
                checksum
        );

        // 5. Atomically apply snapshot into gateway memory
        snapshotManager.applySnapshot(candidateSnapshot);

        // 6. Broadcast CONFIG_UPDATED event to RabbitMQ
        eventPublisher.publish(new ConfigUpdateEvent(nextVersion, publishedAt, checksum, reason));

        log.info("Authoritative snapshot v{} applied (routes: {}, checksum: {})",
                nextVersion, definitions.size(), checksum);

        return nextVersion;
    }

    public long bumpVersion(String component, String reason) {
        ConfigurationVersionEntity versionEntity = versionRepository.findById(component)
                .orElse(new ConfigurationVersionEntity(component, 0L, reason));

        long currentDbVersion = versionEntity.getCurrentVersion();
        long activeInMemoryVersion = snapshotManager != null ? snapshotManager.getActiveVersion() : 0L;
        long nextVersion = Math.max(currentDbVersion, activeInMemoryVersion) + 1;

        versionEntity.setCurrentVersion(nextVersion);
        versionEntity.setUpdatedAt(Instant.now());
        versionEntity.setComment(reason);
        versionRepository.save(versionEntity);

        return nextVersion;
    }

    public Map<String, Long> getAllVersions() {
        return versionRepository.findAll().stream()
                .collect(Collectors.toMap(
                        ConfigurationVersionEntity::getComponent,
                        ConfigurationVersionEntity::getCurrentVersion
                ));
    }

    public GatewayConfigSnapshot getActiveSnapshot() {
        return snapshotManager.getActiveSnapshot();
    }

    private Map<String, List<UpstreamInstance>> loadServiceInstances() {
        Map<String, List<UpstreamInstance>> map = new HashMap<>();
        try {
            List<ServiceInstanceEntity> instances = serviceInstanceRepository.findAll();
            for (ServiceInstanceEntity inst : instances) {
                if (inst.getService() != null && !"DOWN".equalsIgnoreCase(inst.getStatus())) {
                    String serviceId = inst.getService().getServiceId();
                    UpstreamInstance upstream = new UpstreamInstance(
                            inst.getId(),
                            serviceId,
                            inst.getHost(),
                            inst.getPort(),
                            inst.getWeight() > 0 ? inst.getWeight() : 100,
                            InstanceHealthStatus.DOWN
                    );
                    map.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(upstream);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load service instances: {}", e.getMessage());
        }
        return map;
    }

    private RouteDefinition toRouteDefinition(RouteEntity entity) {
        Set<String> methodSet = parseMethods(entity.getMethods());
        String serviceId = entity.getService() != null ? entity.getService().getServiceId() : null;
        UUID clusterId = entity.getService() != null ? entity.getService().getId() : null;
        String rateLimitPolicyName = entity.getRateLimitPolicy() != null ? entity.getRateLimitPolicy().getPolicyName() : null;
        UUID rateLimitPolicyId = entity.getRateLimitPolicy() != null ? entity.getRateLimitPolicy().getId() : null;

        return RouteDefinition.builder()
                .routeId(entity.getRouteId())
                .pathPattern(entity.getPathPattern())
                .methods(methodSet)
                .serviceId(serviceId)
                .clusterId(clusterId)
                .stripPrefixCount(entity.getStripPrefixCount())
                .authRequired(entity.isAuthRequired())
                .connectTimeoutMs(entity.getConnectTimeoutMs())
                .responseTimeoutMs(entity.getResponseTimeoutMs())
                .rateLimitPolicyName(rateLimitPolicyName)
                .rateLimitPolicyId(rateLimitPolicyId)
                .order(entity.getPriority())
                .enabled(entity.isEnabled())
                .build();
    }

    private Set<String> parseMethods(String methodsStr) {
        if (methodsStr == null || methodsStr.isBlank() || methodsStr.equals("*")) {
            return Collections.emptySet();
        }
        return Arrays.stream(methodsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }
}
