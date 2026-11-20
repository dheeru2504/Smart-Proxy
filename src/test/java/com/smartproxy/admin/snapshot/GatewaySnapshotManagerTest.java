package com.smartproxy.admin.snapshot;

import com.smartproxy.core.auth.ApiClientRegistry;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.InMemoryRouteRegistry;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySnapshotManagerTest {

    private SnapshotValidator validator;
    private InMemoryRouteRegistry routeRegistry;
    private UpstreamPoolRegistry upstreamPoolRegistry;
    private ApiClientRegistry apiClientRegistry;
    private GatewaySnapshotManager snapshotManager;

    @BeforeEach
    void setUp() {
        validator = new SnapshotValidator();
        routeRegistry = new InMemoryRouteRegistry();
        upstreamPoolRegistry = new UpstreamPoolRegistry(null);
        apiClientRegistry = new ApiClientRegistry(null);

        snapshotManager = new GatewaySnapshotManager(validator, routeRegistry, upstreamPoolRegistry, apiClientRegistry);
    }

    @Test
    void shouldAtomicallySwapSnapshotAndCalculatePropagationLag() {
        RouteDefinition route = RouteDefinition.builder()
                .routeId("users-route")
                .pathPattern("/users/**")
                .build();

        Instant publishedAt = Instant.now().minus(42, ChronoUnit.MILLIS);

        GatewayConfigSnapshot draft = new GatewayConfigSnapshot(
                1L, publishedAt, null, List.of(route), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), null
        );
        String checksum = SnapshotValidator.computeChecksum(draft);

        GatewayConfigSnapshot candidate = new GatewayConfigSnapshot(
                1L, publishedAt, null, List.of(route), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), checksum
        );

        GatewayConfigSnapshot applied = snapshotManager.applySnapshot(candidate);

        assertNotNull(applied);
        assertEquals(1L, applied.version());
        assertNotNull(applied.appliedAt());
        assertTrue(snapshotManager.getPropagationLagMs() >= 30, "Propagation lag must be tracked accurately");
        assertEquals(1L, snapshotManager.getActiveVersion());
        assertEquals(1, snapshotManager.getActiveSnapshot().routes().size());
        assertEquals(1, routeRegistry.getSnapshot().size());
    }
}
