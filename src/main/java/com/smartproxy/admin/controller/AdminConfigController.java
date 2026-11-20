package com.smartproxy.admin.controller;

import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.admin.snapshot.GatewayConfigSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final ConfigurationSyncService syncService;

    public AdminConfigController(ConfigurationSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/versions")
    public Mono<ResponseEntity<Map<String, Long>>> getVersions() {
        return Mono.fromCallable(syncService::getAllVersions)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/snapshot-status")
    public Mono<ResponseEntity<Map<String, Object>>> getSnapshotStatus() {
        return Mono.fromCallable(() -> {
            GatewayConfigSnapshot snapshot = syncService.getActiveSnapshot();
            Map<String, Object> body = Map.of(
                    "activeVersion", snapshot.version(),
                    "publishedAt", snapshot.publishedAt() != null ? snapshot.publishedAt().toString() : "N/A",
                    "appliedAt", snapshot.appliedAt() != null ? snapshot.appliedAt().toString() : "N/A",
                    "propagationLagMs", snapshot.getPropagationLagMs(),
                    "routeCount", snapshot.routes().size(),
                    "serviceCount", snapshot.serviceInstances().size(),
                    "checksum", snapshot.checksum() != null ? snapshot.checksum() : "N/A",
                    "status", "ACTIVE"
            );
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/sync")
    public Mono<ResponseEntity<Map<String, Object>>> forceSync(@RequestParam(defaultValue = "Manual admin trigger") String reason) {
        return Mono.fromCallable(() -> {
            long newVersion = syncService.syncRoutesFromDatabase(reason);
            GatewayConfigSnapshot snapshot = syncService.getActiveSnapshot();
            Map<String, Object> body = Map.of(
                    "status", "SYNCHRONIZED",
                    "component", "ALL",
                    "version", Long.valueOf(newVersion),
                    "propagationLagMs", Long.valueOf(snapshot.getPropagationLagMs()),
                    "checksum", snapshot.checksum() != null ? snapshot.checksum() : "",
                    "reason", reason
            );
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
