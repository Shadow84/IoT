package com.example.demo.mapper;

import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertRule;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleMapperTest {

    private final AlertRuleMapper mapper = Mappers.getMapper(AlertRuleMapper.class);

    @Test
    void toRule_mapsAllScalarFields() {
        var request = new CreateAlertRuleRequest(
                1L, "temperature", AlertOperator.GREATER_THAN, 75.0, AlertSeverity.HIGH, true);

        var rule = mapper.toRule(request);

        assertThat(rule.getId()).isNull();
        assertThat(rule.getDevice()).isNull();
        assertThat(rule.getMetricName()).isEqualTo("temperature");
        assertThat(rule.getOperator()).isEqualTo(AlertOperator.GREATER_THAN);
        assertThat(rule.getThreshold()).isEqualTo(75.0);
        assertThat(rule.getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(rule.isEnabled()).isTrue();
    }

    @Test
    void toResponse_flattensDeviceFields() {
        var device = Device.builder()
                .id(10L)
                .name("Warehouse Sensor")
                .type(DeviceType.SENSOR)
                .location("Building A")
                .build();

        var rule = AlertRule.builder()
                .id(5L)
                .device(device)
                .metricName("humidity")
                .operator(AlertOperator.LESS_THAN)
                .threshold(30.0)
                .severity(AlertSeverity.MEDIUM)
                .enabled(true)
                .build();

        var response = mapper.toResponse(rule);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.deviceId()).isEqualTo(10L);
        assertThat(response.deviceName()).isEqualTo("Warehouse Sensor");
        assertThat(response.metricName()).isEqualTo("humidity");
        assertThat(response.operator()).isEqualTo(AlertOperator.LESS_THAN);
        assertThat(response.threshold()).isEqualTo(30.0);
        assertThat(response.severity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void updateRule_appliesNonNullFields() {
        var rule = AlertRule.builder()
                .id(3L)
                .metricName("temperature")
                .operator(AlertOperator.GREATER_THAN)
                .threshold(75.0)
                .severity(AlertSeverity.LOW)
                .enabled(true)
                .build();

        var request = new UpdateAlertRuleRequest(null, null, 90.0, AlertSeverity.CRITICAL, null);

        mapper.updateRule(request, rule);

        assertThat(rule.getMetricName()).isEqualTo("temperature");
        assertThat(rule.getOperator()).isEqualTo(AlertOperator.GREATER_THAN);
        assertThat(rule.getThreshold()).isEqualTo(90.0);
        assertThat(rule.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(rule.isEnabled()).isTrue();
    }

    @Test
    void updateRule_allNullRequest_leavesRuleUnchanged() {
        var rule = AlertRule.builder()
                .id(4L)
                .metricName("pressure")
                .operator(AlertOperator.EQUALS)
                .threshold(101.3)
                .severity(AlertSeverity.MEDIUM)
                .enabled(false)
                .build();

        var request = new UpdateAlertRuleRequest(null, null, null, null, null);

        mapper.updateRule(request, rule);

        assertThat(rule.getMetricName()).isEqualTo("pressure");
        assertThat(rule.getOperator()).isEqualTo(AlertOperator.EQUALS);
        assertThat(rule.getThreshold()).isEqualTo(101.3);
        assertThat(rule.getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(rule.isEnabled()).isFalse();
    }
}
