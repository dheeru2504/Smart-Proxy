package com.smartproxy.core.foundation;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility for redacting sensitive headers and credentials from access logs and
 * telemetry.
 */
public final class LogSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_HEADERS = new HashSet<>(Arrays.asList(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "api-key",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "x-jwt-token"));

    private LogSanitizer() {
    }

    /**
     * Sanitizes an HTTP header map, masking credentials and sensitive tokens.
     */
    public static Map<String, String> sanitizeHeaders(HttpHeaders headers) {
        if (headers == null) {
            return Map.of();
        }

        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String headerName = entry.getKey().toLowerCase();
                            if (SENSITIVE_HEADERS.contains(headerName)) {
                                return REDACTED;
                            }
                            return String.join(", ", entry.getValue());
                        },
                        (v1, v2) -> v1));
    }

    /**
     * Sanitizes a single header value by name.
     */
    public static String sanitizeHeader(String headerName, String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        if (headerName != null && SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
            return REDACTED;
        }
        return headerValue;
    }
}
