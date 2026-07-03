package com.example.demo.dto;

import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertStatus;
import com.example.demo.model.OutboxEvent;

import java.time.LocalDateTime;

public record AlertNotificationPayload(
        String deviceName,
        String metricName,
        Double triggerValue,
        Double threshold,
        AlertSeverity severity,
        AlertStatus alertStatus,
        LocalDateTime alertOpenedAt,
        LocalDateTime alertClosedAt
) {
    public static AlertNotificationPayload from(OutboxEvent event) {
        return new AlertNotificationPayload(
                event.getDeviceName(),
                event.getMetricName(),
                event.getTriggerValue(),
                event.getThreshold(),
                event.getSeverity(),
                event.getAlertStatus(),
                event.getAlertOpenedAt(),
                event.getAlertClosedAt()
        );
    }
}
