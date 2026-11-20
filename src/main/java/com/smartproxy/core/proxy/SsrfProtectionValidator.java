package com.smartproxy.core.proxy;

import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/**
 * Validates outbound target URIs to prevent Server-Side Request Forgery (SSRF) and open proxy abuse.
 */
public final class SsrfProtectionValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfProtectionValidator.class);

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254", // AWS/GCP/Azure Cloud Instance Metadata
            "metadata.google.internal",
            "instance-data",
            "100.100.100.200", // Alibaba Cloud metadata
            "0.0.0.0"
    );

    private SsrfProtectionValidator() {}

    /**
     * Validates that the target URI is safe and not pointing to restricted metadata endpoints.
     */
    public static void validateTargetUri(URI targetUri) {
        if (targetUri == null) {
            throw GatewayException.badRequest("Target URI cannot be null.");
        }

        String host = targetUri.getHost();
        if (host == null || host.isBlank()) {
            throw GatewayException.badRequest("Target URI host cannot be blank.");
        }

        String lowerHost = host.toLowerCase().trim();

        // 1. Direct Blocklist Check (Cloud Metadata & Zero IP)
        if (BLOCKED_HOSTS.contains(lowerHost)) {
            log.warn("Blocked SSRF attempt targeting cloud metadata / restricted host: {}", lowerHost);
            throw GatewayException.badRequest("Target upstream host '" + lowerHost + "' is blocked by SSRF protection policy.");
        }

        // 2. Link-Local IPv4 check (169.254.0.0/16)
        if (lowerHost.startsWith("169.254.")) {
            log.warn("Blocked SSRF attempt targeting link-local address: {}", lowerHost);
            throw GatewayException.badRequest("Link-local destination addresses are prohibited.");
        }

        // 3. Scheme check (must be HTTP or HTTPS)
        String scheme = targetUri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw GatewayException.badRequest("Prohibited protocol scheme: " + scheme);
        }
    }
}
