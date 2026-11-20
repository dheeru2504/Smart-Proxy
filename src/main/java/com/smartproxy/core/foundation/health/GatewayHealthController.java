package com.smartproxy.core.foundation.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

/**
 * High-performance non-blocking health and readiness endpoints for container orchestrators and load balancers.
 */
@RestController
public class GatewayHealthController {

    private final Instant startTime = Instant.now();

    @GetMapping(value = {"/health", "/healthz"})
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "service", "smart-proxy",
                "version", "1.0.0",
                "startTime", startTime.toString(),
                "uptimeSeconds", uptimeMs / 1000,
                "jvm", Map.of(
                        "usedMemoryMb", usedMemory / (1024 * 1024),
                        "freeMemoryMb", freeMemory / (1024 * 1024),
                        "totalMemoryMb", totalMemory / (1024 * 1024),
                        "maxMemoryMb", maxMemory / (1024 * 1024),
                        "availableProcessors", runtime.availableProcessors()
                )
        );

        return Mono.just(ResponseEntity.ok(healthInfo));
    }

    @GetMapping("/readyz")
    public Mono<ResponseEntity<Map<String, Object>>> ready() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "READY",
                "service", "smart-proxy",
                "timestamp", Instant.now().toString()
        )));
    }
}
