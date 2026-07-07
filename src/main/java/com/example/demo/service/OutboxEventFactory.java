package com.example.demo.service;

import com.example.demo.model.Alert;
import com.example.demo.model.AlertRule;
import com.example.demo.model.Device;
import com.example.demo.model.OutboxEvent;
import org.springframework.stereotype.Component;

import static com.example.demo.model.OutboxEvent.OutboxStatus.PENDING;
import static java.time.LocalDateTime.now;

@Component
public class OutboxEventFactory {

    public OutboxEvent buildPending(Alert alert, AlertRule rule, Device device) {
        return OutboxEvent.builder()
                .deviceId(device.getId())
                .deviceName(device.getName())
                .metricName(alert.getMetricName())
                .triggerValue(alert.getTriggerValue())
                .threshold(rule.getThreshold())
                .severity(alert.getSeverity())
                .alertStatus(alert.getStatus())
                .alertOpenedAt(alert.getOpenedAt())
                .alertClosedAt(alert.getClosedAt())
                .status(PENDING)
                .attemptCount(0)
                .createdAt(now())
                .build();
    }
}
