package com.example.demo.dto;

import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAlertRuleRequest(
        @NotNull(message = "deviceId must not be null") Long deviceId,
        @NotBlank(message = "metricName must not be blank") String metricName,
        @NotNull(message = "operator must not be null") AlertOperator operator,
        @NotNull(message = "threshold must not be null") Double threshold,
        @NotNull(message = "severity must not be null") AlertSeverity severity,
        boolean enabled
) {}
