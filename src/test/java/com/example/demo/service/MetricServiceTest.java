package com.example.demo.service;

import com.example.demo.dto.CreateMetricRequest;
import com.example.demo.exception.DeviceUnactivatedException;
import com.example.demo.model.Alert;
import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertRule;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertStatus;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceStatus;
import com.example.demo.model.DeviceType;
import com.example.demo.model.Metric;
import com.example.demo.model.OutboxEvent;
import com.example.demo.repository.AlertRepository;
import com.example.demo.repository.AlertRuleRepository;
import com.example.demo.repository.MetricRepository;
import com.example.demo.repository.OutboxEventRepository;
import com.example.demo.service.DeviceService;
import com.example.demo.service.MetricService;
import com.example.demo.service.OutboxEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static com.example.demo.model.OutboxEvent.OutboxStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private MetricRepository metricRepository;
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private OutboxEventFactory outboxEventFactory;

    private MetricService metricService;

    private Device activeDevice;
    private static final LocalDateTime FIXED_TS = LocalDateTime.of(2026, 7, 3, 10, 0, 0);
    private static final OutboxEvent PENDING_EVENT = OutboxEvent.builder()
            .status(OutboxEvent.OutboxStatus.PENDING).attemptCount(0).createdAt(FIXED_TS).build();

    @BeforeEach
    void setUp() {
        metricService = new MetricService(
                deviceService, metricRepository, alertRuleRepository,
                alertRepository, outboxRepository, outboxEventFactory);

        activeDevice = Device.builder()
                .id(1L).name("Temp Sensor").type(DeviceType.SENSOR)
                .location("Building 3").extraAttributes(new HashMap<>())
                .status(DeviceStatus.ACTIVE).build();
    }

    @Test
    void ingest_inactiveDevice_throwsDeviceUnactivatedException() {
        when(deviceService.findActiveOrThrow(2L)).thenThrow(new DeviceUnactivatedException(2L));
        var request = new CreateMetricRequest(2L, "temperature", 80.0, FIXED_TS);

        assertThatThrownBy(() -> metricService.ingest(request))
                .isInstanceOf(DeviceUnactivatedException.class)
                .hasMessageContaining("INACTIVE");

        verifyNoInteractions(metricRepository);
        verifyNoInteractions(alertRuleRepository);
    }

    @Test
    void ingest_noMatchingRules_persistsMetricOnly() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);
        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("temperature").value(80.0).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "temperature"))
                .thenReturn(List.of());

        metricService.ingest(new CreateMetricRequest(1L, "temperature", 80.0, FIXED_TS));

        verify(metricRepository).save(any(Metric.class));
        verifyNoInteractions(alertRepository);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void ingest_violatesRule_noExistingAlert_opensAlertAndCreatesOutboxEvent() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);

        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("temperature").value(87.3).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);

        var rule = AlertRule.builder()
                .id(5L).device(activeDevice).metricName("temperature")
                .operator(AlertOperator.GREATER_THAN).threshold(80.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "temperature"))
                .thenReturn(List.of(rule));

        when(alertRepository.findByRuleIdInAndStatusWithDevice(eq(List.of(5L)), eq(AlertStatus.OPEN)))
                .thenReturn(List.of());

        var savedInstance = Alert.builder().id(100L).device(activeDevice).rule(rule)
                .metricName("temperature").triggerValue(87.3).severity(AlertSeverity.HIGH)
                .openedAt(LocalDateTime.now()).status(AlertStatus.OPEN).build();
        when(alertRepository.save(any(Alert.class))).thenReturn(savedInstance);
        when(outboxEventFactory.buildPending(any(), any(), any())).thenReturn(PENDING_EVENT);

        metricService.ingest(new CreateMetricRequest(1L, "temperature", 87.3, FIXED_TS));

        var instanceCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(instanceCaptor.getValue().getTriggerValue()).isEqualTo(87.3);
        assertThat(instanceCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.HIGH);

        var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(PENDING);
    }

    @Test
    void ingest_violatesRule_existingOpenAlert_doesNotOpenDuplicate() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);

        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("temperature").value(90.0).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);

        var rule = AlertRule.builder()
                .id(5L).device(activeDevice).metricName("temperature")
                .operator(AlertOperator.GREATER_THAN).threshold(80.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "temperature"))
                .thenReturn(List.of(rule));

        var existingAlert = Alert.builder().id(99L).rule(rule).build();
        when(alertRepository.findByRuleIdInAndStatusWithDevice(eq(List.of(5L)), eq(AlertStatus.OPEN)))
                .thenReturn(List.of(existingAlert));

        metricService.ingest(new CreateMetricRequest(1L, "temperature", 90.0, FIXED_TS));

        // No new alert should be saved (save is never called since existing open alert exists)
        verify(alertRepository, never()).save(any(Alert.class));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void ingest_noLongerViolates_closesExistingAlertAndCreatesOutboxEvent() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);

        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("temperature").value(75.0).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);

        var rule = AlertRule.builder()
                .id(5L).device(activeDevice).metricName("temperature")
                .operator(AlertOperator.GREATER_THAN).threshold(80.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "temperature"))
                .thenReturn(List.of(rule));

        var openAlert = Alert.builder().id(99L).device(activeDevice).rule(rule)
                .metricName("temperature").triggerValue(85.0).severity(AlertSeverity.HIGH)
                .openedAt(LocalDateTime.now().minusHours(1)).status(AlertStatus.OPEN).build();
        when(alertRepository.findByRuleIdInAndStatusWithDevice(eq(List.of(5L)), eq(AlertStatus.OPEN)))
                .thenReturn(List.of(openAlert));
        when(alertRepository.save(any(Alert.class))).thenReturn(openAlert);
        when(outboxEventFactory.buildPending(any(), any(), any())).thenReturn(PENDING_EVENT);

        metricService.ingest(new CreateMetricRequest(1L, "temperature", 75.0, FIXED_TS));

        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getStatus()).isEqualTo(AlertStatus.CLOSED);
        assertThat(alertCaptor.getValue().getClosedAt()).isNotNull();

        var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(PENDING);
    }

    @Test
    void ingest_lessThankOperator_violates_opensAlert() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);

        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("battery_level").value(5.0).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);

        var rule = AlertRule.builder()
                .id(6L).device(activeDevice).metricName("battery_level")
                .operator(AlertOperator.LESS_THAN).threshold(10.0)
                .severity(AlertSeverity.HIGH).enabled(true).build();
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "battery_level"))
                .thenReturn(List.of(rule));
        when(alertRepository.findByRuleIdInAndStatusWithDevice(eq(List.of(6L)), eq(AlertStatus.OPEN)))
                .thenReturn(List.of());

        var savedInstance = Alert.builder().id(101L).device(activeDevice).rule(rule)
                .metricName("battery_level").triggerValue(5.0).severity(AlertSeverity.HIGH)
                .openedAt(LocalDateTime.now()).status(AlertStatus.OPEN).build();
        when(alertRepository.save(any(Alert.class))).thenReturn(savedInstance);
        when(outboxEventFactory.buildPending(any(), any(), any())).thenReturn(PENDING_EVENT);

        metricService.ingest(new CreateMetricRequest(1L, "battery_level", 5.0, FIXED_TS));

        verify(alertRepository).save(any(Alert.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void ingest_equalsOperator_exactMatch_opensAlert() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);

        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("cpu_load").value(100.0).timestamp(LocalDateTime.now()).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);

        var rule = AlertRule.builder()
                .id(7L).device(activeDevice).metricName("cpu_load")
                .operator(AlertOperator.EQUALS).threshold(100.0)
                .severity(AlertSeverity.CRITICAL).enabled(true).build();
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "cpu_load"))
                .thenReturn(List.of(rule));
        when(alertRepository.findByRuleIdInAndStatusWithDevice(eq(List.of(7L)), eq(AlertStatus.OPEN)))
                .thenReturn(List.of());

        var savedInstance = Alert.builder().id(102L).device(activeDevice).rule(rule)
                .metricName("cpu_load").triggerValue(100.0).severity(AlertSeverity.CRITICAL)
                .openedAt(LocalDateTime.now()).status(AlertStatus.OPEN).build();
        when(alertRepository.save(any(Alert.class))).thenReturn(savedInstance);

        metricService.ingest(new CreateMetricRequest(1L, "cpu_load", 100.0, FIXED_TS));

        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void ingest_passesTimestampToMetric() {
        when(deviceService.findActiveOrThrow(1L)).thenReturn(activeDevice);
        var ts = LocalDateTime.of(2026, 3, 20, 10, 15, 0);
        var savedMetric = Metric.builder().id(10L).device(activeDevice)
                .metricName("humidity").value(40.0).timestamp(ts).build();
        when(metricRepository.save(any(Metric.class))).thenReturn(savedMetric);
        when(alertRuleRepository.findByDeviceIdAndMetricNameAndEnabledTrue(1L, "humidity"))
                .thenReturn(List.of());

        metricService.ingest(new CreateMetricRequest(1L, "humidity", 40.0, ts));

        var captor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isEqualTo(ts);
    }
}
