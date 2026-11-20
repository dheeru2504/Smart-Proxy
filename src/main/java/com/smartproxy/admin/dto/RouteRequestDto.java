package com.smartproxy.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record RouteRequestDto(
        @NotBlank(message = "routeId is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "routeId must be alphanumeric with dashes or underscores")
        String routeId,

        @NotBlank(message = "pathPattern is required")
        String pathPattern,

        String methods, // e.g. "GET,POST" or "*"

        String serviceId, // Logical serviceId or UUID

        @Min(value = 0, message = "stripPrefixCount must be >= 0")
        int stripPrefixCount,

        boolean authRequired,

        @Min(value = 100, message = "connectTimeoutMs must be >= 100")
        int connectTimeoutMs,

        @Min(value = 100, message = "responseTimeoutMs must be >= 100")
        int responseTimeoutMs,

        String rateLimitPolicyName,

        int priority,

        boolean enabled,

        Long version // For optimistic locking on updates
) {}
