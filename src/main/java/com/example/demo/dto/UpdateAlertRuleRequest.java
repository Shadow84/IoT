package com.example.demo.dto;

import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertSeverity;

public record UpdateAlertRuleRequest(
        String metricName,
        AlertOperator operator,
        Double threshold,
        AlertSeverity severity,
        Boolean enabled
) {}
