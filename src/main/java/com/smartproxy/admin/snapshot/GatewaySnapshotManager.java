package com.smartproxy.admin.snapshot;

import com.smartproxy.core.auth.ApiClientRegistry;
import com.smartproxy.core.routing.RouteRegistry;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the in-memory lifecycle of immutable gateway configuration snapshots.
 * Ensures atomic swap and tracks real-time propagation lag.
 */
@Component
public class GatewaySnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(GatewaySnapshotManager.class);

    private final AtomicReference<GatewayConfigSnapshot> activeSnapshot =
            new AtomicReference<>(GatewayConfigSnapshot.initial());

    private final SnapshotValidator validator;
    private final RouteRegistry routeRegistry;
    private final UpstreamPoolRegistry upstreamPoolRegistry;
    private final ApiClientRegistry apiClientRegistry;

    public GatewaySnapshotManager(SnapshotValidator validator,
                                  RouteRegistry routeRegistry,
                                  UpstreamPoolRegistry upstreamPoolRegistry,
                                  ApiClientRegistry apiClientRegistry) {
        this.validator = validator;
        this.routeRegistry = routeRegistry;
        this.upstreamPoolRegistry = upstreamPoolRegistry;
        this.apiClientRegistry = apiClientRegistry;
    }

    /**
     * Atomically validates and applies a new candidate configuration snapshot.
     */
    public synchronized GatewayConfigSnapshot applySnapshot(GatewayConfigSnapshot candidate) {
        GatewayConfigSnapshot current = activeSnapshot.get();
        validator.validate(candidate, current);

        Instant now = Instant.now();
        GatewayConfigSnapshot applied = candidate.withAppliedAt(now);

        // 1. Swap active snapshot reference
        activeSnapshot.set(applied);

        // 2. Hot-swap in-memory subsystem registries
        if (routeRegistry != null && applied.routes() != null) {
            routeRegistry.reload(applied.routes());
        }

        if (upstreamPoolRegistry != null && applied.serviceInstances() != null) {
            applied.serviceInstances().forEach((serviceId, instances) -> {
                if (instances != null) {
                    upstreamPoolRegistry.setPool(serviceId, instances);
                }
            });
        }

        if (apiClientRegistry != null && applied.apiClients() != null) {
            applied.apiClients().values().forEach(apiClientRegistry::put);
        }

        log.info("Successfully swapped to configuration snapshot v{} (routes: {}, propagation lag: {}ms, checksum: {})",
                applied.version(), applied.routes().size(), applied.getPropagationLagMs(), applied.checksum());

        return applied;
    }

    public GatewayConfigSnapshot getActiveSnapshot() {
        return activeSnapshot.get();
    }

    public long getActiveVersion() {
        return activeSnapshot.get().version();
    }

    public long getPropagationLagMs() {
        return activeSnapshot.get().getPropagationLagMs();
    }
}
