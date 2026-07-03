package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateMetricRequest(
        @NotNull(message = "deviceId must not be null") Long deviceId,
        @NotBlank(message = "metricName must not be blank") String metricName,
        @NotNull(message = "value must not be null") Double value,
        @NotNull(message = "timestamp must not be null") LocalDateTime timestamp
) {}
