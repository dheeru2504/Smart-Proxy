package com.smartproxy.admin.snapshot;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates candidate configuration snapshots for integrity, consistency, and monotonic versioning.
 */
@Component
public class SnapshotValidator {

    private static final Logger log = LoggerFactory.getLogger(SnapshotValidator.class);

    /**
     * Validates a candidate snapshot against the currently active snapshot.
     */
    public void validate(GatewayConfigSnapshot candidate, GatewayConfigSnapshot current) {
        if (candidate == null) {
            throw GatewayException.badRequest("Candidate configuration snapshot cannot be null.");
        }

        // 1. Monotonic Version Verification
        if (current != null && current.version() > 0 && candidate.version() <= current.version()) {
            throw GatewayException.badRequest(
                    "Snapshot version " + candidate.version() + " is not greater than active version " + current.version()
            );
        }

        // 2. Route Consistency & Duplicate Verification
        Set<String> routeIds = new HashSet<>();
        for (RouteDefinition route : candidate.routes()) {
            if (route.getRouteId() == null || route.getRouteId().isBlank()) {
                throw GatewayException.badRequest("Route ID cannot be blank in snapshot v" + candidate.version());
            }
            if (route.getPathPattern() == null || route.getPathPattern().isBlank()) {
                throw GatewayException.badRequest("Route path pattern cannot be blank for route: " + route.getRouteId());
            }
            if (!routeIds.add(route.getRouteId())) {
                throw GatewayException.badRequest("Duplicate route ID '" + route.getRouteId() + "' detected in snapshot.");
            }
        }

        // 3. Checksum Verification
        String computedChecksum = computeChecksum(candidate);
        if (candidate.checksum() != null && !candidate.checksum().equals(computedChecksum)) {
            log.warn("Snapshot checksum mismatch: declared='{}', computed='{}'", candidate.checksum(), computedChecksum);
            throw GatewayException.badRequest("Snapshot integrity checksum mismatch.");
        }
    }

    /**
     * Computes deterministic SHA-256 checksum of the snapshot content.
     */
    public static String computeChecksum(GatewayConfigSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append("v:").append(snapshot.version()).append(";");
            for (RouteDefinition route : snapshot.routes()) {
                sb.append(route.getRouteId()).append(":").append(route.getPathPattern()).append(";");
            }
            if (snapshot.serviceInstances() != null) {
                snapshot.serviceInstances().forEach((serviceId, instances) -> {
                    sb.append("svc:").append(serviceId).append("=").append(instances.size()).append(";");
                });
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encode(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
