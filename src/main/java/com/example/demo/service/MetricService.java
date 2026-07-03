package com.example.demo.service;

import com.example.demo.dto.CreateMetricRequest;
import com.example.demo.model.Alert;
import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertRule;
import com.example.demo.model.Device;
import com.example.demo.model.Metric;
import com.example.demo.repository.AlertRepository;
import com.example.demo.repository.AlertRuleRepository;
import com.example.demo.repository.MetricRepository;
import com.example.demo.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.demo.model.AlertStatus.CLOSED;
import static com.example.demo.model.AlertStatus.OPEN;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricService {

    private final DeviceService deviceService;
    private final MetricRepository metricRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertRepository alertRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Transactional
    public void ingest(CreateMetricRequest request) {
        var device = deviceService.findActiveOrThrow(request.deviceId());

        var metric = metricRepository.save(Metric.builder()
                .device(device)
                .metricName(request.metricName())
                .value(request.value())
                .timestamp(request.timestamp())
                .build());

        log.debug("Persisted metric id={} for device id={}, metric='{}', value={}.",
                metric.getId(), device.getId(), request.metricName(), request.value());

        var rules = alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(
                device.getId(), request.metricName());

        if (rules.isEmpty()) {
            log.debug("No rules for device id={} , metric='{}', value={}.",
                    device.getId(), request.metricName(), request.value());
            return;
        }

        var openedAlerts = getOpenedAlerts(rules);

        rules
                .forEach(rule -> evaluateRule(rule, metric, openedAlerts, device));
    }

    private void evaluateRule(AlertRule rule, Metric metric,
                              Map<Long, Alert> openedAlerts, Device device) {

        var violates = evaluateCondition(metric.getValue(), rule.getOperator(), rule.getThreshold());
        var openAlert = openedAlerts.get(rule.getId());

        if (violates && isNull(openAlert)) {
            openAlert(device, rule, metric);
        } else if (!violates && nonNull(openAlert)) {
            closeAlert(device, openAlert, rule, metric);
        }
    }

    private boolean evaluateCondition(double value, AlertOperator operator, double threshold) {
        return switch (operator) {
            case GREATER_THAN -> value > threshold;
            case LESS_THAN -> value < threshold;
            case EQUALS -> Double.compare(value, threshold) == 0;
        };
    }

    private @NonNull Map<Long, Alert> getOpenedAlerts(List<AlertRule> rules) {
        var ruleIds = rules
                .stream()
                .map(AlertRule::getId)
                .toList();

        return alertRepository.findByRuleIdInAndStatusWithDevice(ruleIds, OPEN)
                .stream()
                .collect(Collectors.toMap(a -> a.getRule().getId(), a -> a));
    }

    private void openAlert(Device device, AlertRule rule, Metric metric) {
        var alert = alertRepository.save(Alert.builder()
                .device(device)
                .rule(rule)
                .metricName(metric.getMetricName())
                .triggerValue(metric.getValue())
                .severity(rule.getSeverity())
                .openedAt(metric.getTimestamp())
                .status(OPEN)
                .build());

        var outboxEvent = outboxEventFactory.buildPending(alert, rule, device);
        outboxRepository.save(outboxEvent);

        log.warn("ALERT OPENED: id={}, device='{}', metric='{}', value={} {} {} (severity={}).",
                alert.getId(),
                device.getName(),
                metric.getMetricName(),
                metric.getValue(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getSeverity());
    }

    private void closeAlert(Device device, Alert alert, AlertRule rule, Metric metric) {
        alert.setStatus(CLOSED);
        alert.setClosedAt(metric.getTimestamp());
        alertRepository.save(alert);

        var outboxEvent = outboxEventFactory.buildPending(alert, rule, device);
        outboxRepository.save(outboxEvent);

        log.info("ALERT CLOSED: id={}, device='{}', metric='{}', value={} no longer violates rule.",
                alert.getId(),
                device.getName(),
                metric.getMetricName(),
                metric.getValue());
    }
}
