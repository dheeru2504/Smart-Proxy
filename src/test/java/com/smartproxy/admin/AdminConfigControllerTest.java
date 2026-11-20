package com.smartproxy.admin;

import com.smartproxy.admin.controller.AdminConfigController;
import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.storage.entity.RouteEntity;
import com.smartproxy.storage.repository.ConfigurationVersionRepository;
import com.smartproxy.storage.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminConfigControllerTest {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ConfigurationVersionRepository versionRepository;

    @Autowired
    private ConfigurationSyncService syncService;

    @Autowired
    private AdminConfigController configController;

    @BeforeEach
    void setUp() {
        routeRepository.deleteAll();
        versionRepository.deleteAll();
    }

    @Test
    void shouldTrackConfigurationVersions() {
        syncService.bumpVersion("ROUTES", "Initial test setup");
        syncService.bumpVersion("SERVICES", "Initial service setup");

        ResponseEntity<Map<String, Long>> response = configController.getVersions().block();

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("ROUTES"));
        assertTrue(response.getBody().containsKey("SERVICES"));
        assertTrue(response.getBody().get("ROUTES") >= 1L);
        assertTrue(response.getBody().get("SERVICES") >= 1L);
    }

    @Test
    void shouldTriggerManualForceSyncAndReturnSnapshotStatus() {
        RouteEntity entity = new RouteEntity();
        entity.setRouteId("manual-sync-test");
        entity.setPathPattern("/api/sync/**");
        entity.setPriority(0);
        entity.setEnabled(true);
        routeRepository.save(entity);

        ResponseEntity<Map<String, Object>> response = configController.forceSync("Test sync").block();

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("SYNCHRONIZED", response.getBody().get("status"));
        assertEquals("ALL", response.getBody().get("component"));

        ResponseEntity<Map<String, Object>> statusResp = configController.getSnapshotStatus().block();
        assertNotNull(statusResp);
        assertNotNull(statusResp.getBody());
        assertEquals("ACTIVE", statusResp.getBody().get("status"));
        assertEquals(1, statusResp.getBody().get("routeCount"));
    }
}
