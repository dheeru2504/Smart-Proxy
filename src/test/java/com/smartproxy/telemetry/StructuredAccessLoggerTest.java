package com.smartproxy.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StructuredAccessLoggerTest {

    @Test
    void shouldSerializeAndLogAccessEntryCleanly() {
        StructuredAccessLogger logger = new StructuredAccessLogger(new ObjectMapper());

        AccessLogEntry entry = new AccessLogEntry(
                "req-uuid-101",
                "req-uuid-101",
                Instant.now(),
                "GET",
                "/api/v1/orders/123",
                "includeDetails=true",
                200,
                14L,
                "192.168.1.50",
                "client-mobile-app",
                "tenant-xyz",
                "order-service",
                "10.0.0.12:8080",
                "order-route",
                0,
                false,
                "CLOSED",
                "Mozilla/5.0"
        );

        assertDoesNotThrow(() -> logger.logAccess(entry));
    }
}
