package com.smartproxy.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceInstanceRequestDto(
        @NotBlank(message = "host is required")
        String host,

        @Min(value = 1, message = "port must be between 1 and 65535")
        @Max(value = 65535, message = "port must be between 1 and 65535")
        int port,

        @Min(value = 1, message = "weight must be >= 1")
        int weight,

        @Pattern(regexp = "^(HEALTHY|UNHEALTHY|SUSPENDED|DRAINING)$",
                message = "Status must be HEALTHY, UNHEALTHY, SUSPENDED, or DRAINING")
        String status,

        Long version
) {}
