package com.example.demo.dto;

import com.example.demo.model.Alert;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertStatus;

import java.time.LocalDateTime;

public record AlertInstanceResponse(
        Long id,
        Long deviceId,
        String deviceName,
        Long ruleId,
        String metricName,
        Double triggerValue,
        AlertSeverity severity,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        AlertStatus status
) {
    public static AlertInstanceResponse from(Alert instance) {
        return new AlertInstanceResponse(
                instance.getId(),
                instance.getDevice().getId(),
                instance.getDevice().getName(),
                instance.getRule().getId(),
                instance.getMetricName(),
                instance.getTriggerValue(),
                instance.getSeverity(),
                instance.getOpenedAt(),
                instance.getClosedAt(),
                instance.getStatus()
        );
    }
}
