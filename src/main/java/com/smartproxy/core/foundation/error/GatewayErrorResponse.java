package com.smartproxy.core.foundation.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized structured error response model conforming to RFC 7807 (Problem Details).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        Map<String, Object> details
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Instant timestamp = Instant.now();
        private int status = 500;
        private String error = "Internal Server Error";
        private String code = "GATEWAY_INTERNAL_ERROR";
        private String message = "An unexpected error occurred.";
        private String path = "/";
        private String traceId = "UNKNOWN";
        private Map<String, Object> details;

        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }

        public GatewayErrorResponse build() {
            return new GatewayErrorResponse(timestamp, status, error, code, message, path, traceId, details);
        }
    }
}
