package com.smartproxy.admin;

import com.smartproxy.admin.controller.AdminRouteController;
import com.smartproxy.admin.dto.RouteRequestDto;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteRegistry;
import com.smartproxy.storage.entity.RouteEntity;
import com.smartproxy.storage.repository.ConfigurationVersionRepository;
import com.smartproxy.storage.repository.RateLimitPolicyRepository;
import com.smartproxy.storage.repository.RouteRepository;
import com.smartproxy.storage.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminRouteControllerTest {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private RateLimitPolicyRepository rateLimitPolicyRepository;

    @Autowired
    private ConfigurationVersionRepository versionRepository;

    @Autowired
    private RouteRegistry routeRegistry;

    @Autowired
    private ConfigurationSyncService syncService;

    @Autowired
    private AdminRouteController controller;

    @BeforeEach
    void setUp() {
        routeRepository.deleteAll();
    }

    @Test
    void shouldCreateRouteAndInstantlySyncInMemorySnapshot() {
        RouteRequestDto dto = new RouteRequestDto(
                "payments-service",
                "/api/v1/payments/**",
                "POST,GET",
                null,
                1,
                true,
                3000,
                5000,
                null,
                5,
                true,
                null
        );

        ResponseEntity<RouteEntity> response = controller.createRoute(dto).block();

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("payments-service", response.getBody().getRouteId());
        assertEquals(0L, response.getBody().getVersion());

        // Verify In-Memory snapshot has been hot-reloaded without querying DB on request
        Optional<RouteDefinition> inMemoryMatch = routeRegistry.match("POST", "/api/v1/payments/charge");
        assertTrue(inMemoryMatch.isPresent());
        assertEquals("payments-service", inMemoryMatch.get().getRouteId());
        assertEquals(1, inMemoryMatch.get().getStripPrefixCount());
    }

    @Test
    void shouldEnforceOptimisticLockingOnConcurrentUpdates() {
        RouteRequestDto createDto = new RouteRequestDto(
                "orders-v2",
                "/api/v2/orders/**",
                "GET",
                null,
                0,
                false,
                3000,
                5000,
                null,
                0,
                true,
                null
        );

        RouteEntity created = controller.createRoute(createDto).block().getBody();
        assertNotNull(created);

        // Update 1 with correct version
        RouteRequestDto update1 = new RouteRequestDto(
                "orders-v2",
                "/api/v2/orders/**",
                "GET,POST",
                null,
                0,
                false,
                3000,
                5000,
                null,
                0,
                true,
                created.getVersion()
        );

        RouteEntity updated = controller.updateRoute(created.getId(), update1).block().getBody();
        assertNotNull(updated);
        assertEquals(1L, updated.getVersion());

        // Update 2 with STALE version (0L instead of 1L) -> Expect 409 Conflict
        RouteRequestDto staleUpdate = new RouteRequestDto(
                "orders-v2",
                "/api/v2/orders/**",
                "DELETE",
                null,
                0,
                false,
                3000,
                5000,
                null,
                0,
                true,
                0L // Outdated version!
        );

        Exception ex = assertThrows(Exception.class, () -> controller.updateRoute(created.getId(), staleUpdate).block());
        assertTrue(ex.getMessage().contains("OPTIMISTIC_LOCK_FAILURE") || ex.getMessage().contains("modified concurrently"));
    }
}
