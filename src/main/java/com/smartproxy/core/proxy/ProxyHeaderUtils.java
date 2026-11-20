package com.smartproxy.core.proxy;

import com.smartproxy.core.auth.ClientIdentity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

/**
 * Utility for stripping RFC 7230 hop-by-hop headers, cleansing spoofed identity headers,
 * preventing secret leakage, and injecting verified proxy headers.
 */
public final class ProxyHeaderUtils {

    private static final Set<String> HOP_BY_HOP_HEADERS = new HashSet<>(Arrays.asList(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "content-length"
    ));

    private static final Set<String> UNTRUSTED_IDENTITY_HEADER_PREFIXES = Set.of(
            "x-authenticated-",
            "x-user-",
            "x-tenant-",
            "x-roles",
            "x-scopes",
            "x-client-id",
            "x-gateway-"
    );

    private static final Set<String> SENSITIVE_RESPONSE_HEADERS = Set.of(
            "server",
            "x-powered-by",
            "x-aspnet-version",
            "x-runtime",
            "x-sourcefiles"
    );

    private ProxyHeaderUtils() {}

    /**
     * Builds clean upstream request headers by stripping hop-by-hop headers and adding X-Forwarded-* headers.
     */
    public static HttpHeaders buildUpstreamHeaders(ServerHttpRequest request, URI targetUri, String traceId) {
        return buildUpstreamHeaders(request, targetUri, traceId, null);
    }

    /**
     * Builds clean upstream request headers by stripping hop-by-hop headers, removing spoofed client headers,
     * adding X-Forwarded-*, and injecting verified client identity headers.
     */
    public static HttpHeaders buildUpstreamHeaders(ServerHttpRequest request, URI targetUri,
                                                   String traceId, ClientIdentity identity) {
        HttpHeaders upstreamHeaders = new HttpHeaders();

        // 1. Copy client request headers, omitting hop-by-hop AND spoofed client identity headers
        request.getHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lower) && !lower.equals("host") && !isUntrustedIdentityHeader(lower)) {
                upstreamHeaders.put(name, new ArrayList<>(values));
            }
        });

        // 2. Set target Host header
        if (targetUri.getPort() > 0 && targetUri.getPort() != 80 && targetUri.getPort() != 443) {
            upstreamHeaders.set(HttpHeaders.HOST, targetUri.getHost() + ":" + targetUri.getPort());
        } else {
            upstreamHeaders.set(HttpHeaders.HOST, targetUri.getHost());
        }

        // 3. Inject X-Forwarded-For
        String clientIp = resolveClientIp(request);
        String existingXff = request.getHeaders().getFirst("X-Forwarded-For");
        if (existingXff != null && !existingXff.isBlank()) {
            upstreamHeaders.set("X-Forwarded-For", existingXff + ", " + clientIp);
        } else {
            upstreamHeaders.set("X-Forwarded-For", clientIp);
        }

        // 4. Inject X-Forwarded-Proto, Host, Port
        String scheme = request.getURI().getScheme();
        upstreamHeaders.set("X-Forwarded-Proto", scheme != null ? scheme : "http");

        String origHost = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (origHost != null) {
            upstreamHeaders.set("X-Forwarded-Host", origHost);
        }

        int origPort = request.getURI().getPort();
        if (origPort > 0) {
            upstreamHeaders.set("X-Forwarded-Port", String.valueOf(origPort));
        }

        // 5. Inject Trace ID
        if (traceId != null) {
            upstreamHeaders.set("X-Request-ID", traceId);
            upstreamHeaders.set("X-Gateway-Trace-ID", traceId);
        }

        // 6. Inject Verified Identity Headers (Zero-Trust)
        if (identity != null && identity.authenticated()) {
            upstreamHeaders.set("X-Authenticated-Client", identity.clientId());
            upstreamHeaders.set("X-Authenticated-Tenant", identity.tenantId());
            if (!identity.roles().isEmpty()) {
                upstreamHeaders.set("X-Authenticated-Roles", String.join(",", identity.roles()));
            }
            if (!identity.scopes().isEmpty()) {
                upstreamHeaders.set("X-Authenticated-Scopes", String.join(",", identity.scopes()));
            }
            upstreamHeaders.set("X-Authenticated-Type", identity.authType().name());
        }

        return upstreamHeaders;
    }

    /**
     * Filters upstream response headers before sending back to client, stripping hop-by-hop
     * and sensitive internal infrastructure headers to prevent secret leakage.
     */
    public static HttpHeaders filterResponseHeaders(HttpHeaders responseHeaders) {
        HttpHeaders cleanHeaders = new HttpHeaders();
        if (responseHeaders == null) {
            return cleanHeaders;
        }

        responseHeaders.forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lower) && !SENSITIVE_RESPONSE_HEADERS.contains(lower)) {
                cleanHeaders.put(name, new ArrayList<>(values));
            }
        });

        return cleanHeaders;
    }

    private static boolean isUntrustedIdentityHeader(String lowerHeaderName) {
        for (String prefix : UNTRUSTED_IDENTITY_HEADER_PREFIXES) {
            if (lowerHeaderName.startsWith(prefix) || lowerHeaderName.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "127.0.0.1";
    }
}
