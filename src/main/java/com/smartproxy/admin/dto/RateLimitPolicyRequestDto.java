package com.smartproxy.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RateLimitPolicyRequestDto(
        @NotBlank(message = "policyName is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "policyName must be alphanumeric with dashes or underscores")
        String policyName,

        @NotBlank(message = "algorithm is required")
        @Pattern(regexp = "^(TOKEN_BUCKET|SLIDING_WINDOW_LOG|LEAKY_BUCKET)$",
                message = "Algorithm must be TOKEN_BUCKET, SLIDING_WINDOW_LOG, or LEAKY_BUCKET")
        String algorithm,

        @Min(value = 1, message = "replenishRate must be >= 1")
        int replenishRate,

        @Min(value = 1, message = "burstCapacity must be >= 1")
        int burstCapacity,

        @Min(value = 1, message = "windowDurationSec must be >= 1")
        int windowDurationSec,

        @NotBlank(message = "limitBy is required")
        @Pattern(regexp = "^(IP|API_KEY|USER|ROUTE)$", message = "limitBy must be IP, API_KEY, USER, or ROUTE")
        String limitBy,

        Long version
) {}
