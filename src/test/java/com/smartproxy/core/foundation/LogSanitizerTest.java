package com.smartproxy.core.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {

    @Test
    void shouldRedactSensitiveHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.secret");
        headers.add("X-API-Key", "sp_live_9988776655");
        headers.add("Cookie", "SESSIONID=xyz123; Path=/");
        headers.add("Content-Type", "application/json");
        headers.add("User-Agent", "Mozilla/5.0");

        Map<String, String> sanitized = LogSanitizer.sanitizeHeaders(headers);

        assertEquals("[REDACTED]", sanitized.get("Authorization"));
        assertEquals("[REDACTED]", sanitized.get("X-API-Key"));
        assertEquals("[REDACTED]", sanitized.get("Cookie"));
        assertEquals("application/json", sanitized.get("Content-Type"));
        assertEquals("Mozilla/5.0", sanitized.get("User-Agent"));
    }

    @Test
    void shouldSanitizeSingleHeader() {
        assertEquals("[REDACTED]", LogSanitizer.sanitizeHeader("authorization", "Bearer xyz"));
        assertEquals("[REDACTED]", LogSanitizer.sanitizeHeader("X-Api-Key", "secret-key"));
        assertEquals("public-val", LogSanitizer.sanitizeHeader("X-Custom", "public-val"));
    }
}
