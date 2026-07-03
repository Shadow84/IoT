package com.example.demo.dto;

import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertSeverity;

public record AlertRuleResponse(
        Long id,
        Long deviceId,
        String deviceName,
        String metricName,
        AlertOperator operator,
        Double threshold,
        AlertSeverity severity,
        boolean enabled
) {}
