package com.smartproxy.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record ApiClientRequestDto(
        @NotBlank(message = "clientId is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "clientId must be alphanumeric with dashes or underscores")
        String clientId,

        @NotBlank(message = "clientName is required")
        String clientName,

        @NotBlank(message = "rawApiKey is required (will be securely hashed)")
        String rawApiKey,

        String role,

        String rateLimitPolicyName,

        boolean enabled,

        Instant expiresAt,

        Long version
) {}
