package com.example.demo.service;

import com.example.demo.dto.AlertRuleResponse;
import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.mapper.AlertRuleMapper;
import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertRule;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceStatus;
import com.example.demo.model.DeviceType;
import com.example.demo.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @Mock
    private DeviceService deviceService;

    private AlertRuleService alertRuleService;

    private Device device;

    @BeforeEach
    void setUp() {
        alertRuleService = new AlertRuleService(alertRuleRepository, alertRuleMapper, deviceService);
        device = Device.builder()
                .id(1L)
                .name("Warehouse Sensor")
                .type(DeviceType.SENSOR)
                .location("Building A")
                .status(DeviceStatus.ACTIVE)
                .build();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        var request = new CreateAlertRuleRequest(
                1L, "temperature", AlertOperator.GREATER_THAN, 75.0, AlertSeverity.HIGH, true);

        var rule = AlertRule.builder()
                .metricName("temperature")
                .operator(AlertOperator.GREATER_THAN)
                .threshold(75.0)
                .severity(AlertSeverity.HIGH)
                .enabled(true)
                .build();

        var saved = AlertRule.builder()
                .id(10L)
                .device(device)
                .metricName("temperature")
                .operator(AlertOperator.GREATER_THAN)
                .threshold(75.0)
                .severity(AlertSeverity.HIGH)
                .enabled(true)
                .build();

        var expected = new AlertRuleResponse(
                10L, 1L, "Warehouse Sensor", "temperature",
                AlertOperator.GREATER_THAN, 75.0, AlertSeverity.HIGH, true);

        when(deviceService.findOrThrow(1L)).thenReturn(device);
        when(alertRuleMapper.toRule(request)).thenReturn(rule);
        when(alertRuleRepository.save(any(AlertRule.class))).thenReturn(saved);
        when(alertRuleMapper.toResponse(saved)).thenReturn(expected);

        var response = alertRuleService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.deviceId()).isEqualTo(1L);
        assertThat(response.metricName()).isEqualTo("temperature");
        assertThat(response.severity()).isEqualTo(AlertSeverity.HIGH);
        verify(alertRuleMapper).toRule(request);
        verify(alertRuleRepository).save(rule);
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsMappedResponses() {
        var rule1 = AlertRule.builder().id(1L).device(device).metricName("temp")
                .operator(AlertOperator.GREATER_THAN).threshold(75.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        var rule2 = AlertRule.builder().id(2L).device(device).metricName("humidity")
                .operator(AlertOperator.LESS_THAN).threshold(20.0)
                .severity(AlertSeverity.LOW).enabled(false).build();

        var r1 = new AlertRuleResponse(1L, 1L, "Warehouse Sensor", "temp",
                AlertOperator.GREATER_THAN, 75.0, AlertSeverity.HIGH, true);
        var r2 = new AlertRuleResponse(2L, 1L, "Warehouse Sensor", "humidity",
                AlertOperator.LESS_THAN, 20.0, AlertSeverity.LOW, false);

        when(alertRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));
        when(alertRuleMapper.toResponse(rule1)).thenReturn(r1);
        when(alertRuleMapper.toResponse(rule2)).thenReturn(r2);

        var result = alertRuleService.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).metricName()).isEqualTo("temp");
        assertThat(result.get(1).metricName()).isEqualTo("humidity");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_existingId_returnsResponse() {
        var rule = AlertRule.builder().id(5L).device(device).metricName("pressure")
                .operator(AlertOperator.EQUALS).threshold(101.3)
                .severity(AlertSeverity.MEDIUM).enabled(true).build();
        var expected = new AlertRuleResponse(5L, 1L, "Warehouse Sensor", "pressure",
                AlertOperator.EQUALS, 101.3, AlertSeverity.MEDIUM, true);

        when(alertRuleRepository.findById(5L)).thenReturn(Optional.of(rule));
        when(alertRuleMapper.toResponse(rule)).thenReturn(expected);

        var response = alertRuleService.getById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.metricName()).isEqualTo("pressure");
    }

    @Test
    void getById_missingId_throwsEntityNotFoundException() {
        when(alertRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertRuleService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_delegatesToMapperAndSaves() {
        var rule = AlertRule.builder().id(3L).device(device).metricName("temperature")
                .operator(AlertOperator.GREATER_THAN).threshold(75.0)
                .severity(AlertSeverity.LOW).enabled(true).build();
        var request = new UpdateAlertRuleRequest(null, null, 90.0, AlertSeverity.CRITICAL, null);
        var expected = new AlertRuleResponse(3L, 1L, "Warehouse Sensor", "temperature",
                AlertOperator.GREATER_THAN, 90.0, AlertSeverity.CRITICAL, true);

        when(alertRuleRepository.findById(3L)).thenReturn(Optional.of(rule));
        when(alertRuleRepository.save(rule)).thenReturn(rule);
        when(alertRuleMapper.toResponse(rule)).thenReturn(expected);

        var response = alertRuleService.update(3L, request);

        verify(alertRuleMapper).updateRule(request, rule);
        assertThat(response.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(response.threshold()).isEqualTo(90.0);
    }

    @Test
    void update_missingId_throwsEntityNotFoundException() {
        when(alertRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertRuleService.update(99L,
                new UpdateAlertRuleRequest(null, null, null, null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── enable / disable ──────────────────────────────────────────────────────

    @Test
    void enable_setsEnabledTrue() {
        var rule = AlertRule.builder().id(2L).device(device).metricName("temp")
                .operator(AlertOperator.GREATER_THAN).threshold(75.0)
                .severity(AlertSeverity.LOW).enabled(false).build();
        when(alertRuleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(alertRuleRepository.save(any(AlertRule.class))).thenReturn(rule);

        alertRuleService.enable(2L);

        assertThat(rule.isEnabled()).isTrue();
        verify(alertRuleRepository).save(rule);
    }

    @Test
    void disable_setsEnabledFalse() {
        var rule = AlertRule.builder().id(2L).device(device).metricName("temp")
                .operator(AlertOperator.GREATER_THAN).threshold(75.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        when(alertRuleRepository.findById(2L)).thenReturn(Optional.of(rule));
        when(alertRuleRepository.save(any(AlertRule.class))).thenReturn(rule);

        alertRuleService.disable(2L);

        assertThat(rule.isEnabled()).isFalse();
        verify(alertRuleRepository).save(rule);
    }

    @Test
    void enable_missingId_throwsEntityNotFoundException() {
        when(alertRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertRuleService.enable(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void disable_missingId_throwsEntityNotFoundException() {
        when(alertRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertRuleService.disable(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingId_deletesRule() {
        when(alertRuleRepository.existsById(7L)).thenReturn(true);

        alertRuleService.delete(7L);

        verify(alertRuleRepository).deleteById(7L);
    }

    @Test
    void delete_missingId_throwsEntityNotFoundException() {
        when(alertRuleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> alertRuleService.delete(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
