package com.smartproxy.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceRequestDto(
        @NotBlank(message = "serviceId is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "serviceId must be alphanumeric with dashes or underscores")
        String serviceId,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "loadBalancerStrategy is required")
        @Pattern(regexp = "^(ROUND_ROBIN|WEIGHTED_ROUND_ROBIN|LEAST_CONNECTIONS|IP_HASH|RANDOM)$",
                message = "Strategy must be ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, LEAST_CONNECTIONS, IP_HASH, or RANDOM")
        String loadBalancerStrategy,

        @Min(value = 100, message = "connectTimeoutMs must be >= 100")
        int connectTimeoutMs,

        @Min(value = 100, message = "readTimeoutMs must be >= 100")
        int readTimeoutMs,

        @NotBlank(message = "healthCheckPath is required")
        String healthCheckPath,

        @Min(value = 1, message = "healthCheckIntervalSec must be >= 1")
        int healthCheckIntervalSec,

        @Min(value = 1, message = "unhealthyThreshold must be >= 1")
        int unhealthyThreshold,

        @Min(value = 1, message = "healthyThreshold must be >= 1")
        int healthyThreshold,

        Long version
) {}
