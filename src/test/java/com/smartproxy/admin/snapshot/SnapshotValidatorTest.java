package com.smartproxy.admin.snapshot;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotValidatorTest {

    private SnapshotValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SnapshotValidator();
    }

    @Test
    void shouldValidateValidSnapshotSuccessfully() {
        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-route")
                .pathPattern("/orders/**")
                .build();

        GatewayConfigSnapshot draft = new GatewayConfigSnapshot(
                1L, Instant.now(), null, List.of(route), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), null
        );
        String checksum = SnapshotValidator.computeChecksum(draft);

        GatewayConfigSnapshot candidate = new GatewayConfigSnapshot(
                1L, Instant.now(), null, List.of(route), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), checksum
        );

        assertDoesNotThrow(() -> validator.validate(candidate, GatewayConfigSnapshot.initial()));
    }

    @Test
    void shouldRejectNonMonotonicVersion() {
        GatewayConfigSnapshot current = new GatewayConfigSnapshot(
                5L, Instant.now(), Instant.now(), Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), "chk-5"
        );

        GatewayConfigSnapshot candidate = new GatewayConfigSnapshot(
                4L, Instant.now(), null, Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), "chk-4"
        );

        assertThrows(GatewayException.class, () -> validator.validate(candidate, current));
    }

    @Test
    void shouldRejectChecksumMismatch() {
        RouteDefinition route = RouteDefinition.builder()
                .routeId("orders-route")
                .pathPattern("/orders/**")
                .build();

        GatewayConfigSnapshot candidate = new GatewayConfigSnapshot(
                1L, Instant.now(), null, List.of(route), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), "tampered-checksum"
        );

        assertThrows(GatewayException.class, () -> validator.validate(candidate, GatewayConfigSnapshot.initial()));
    }
}
