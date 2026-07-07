package com.example.demo.service;

import com.example.demo.client.AlertNotificationClient;
import com.example.demo.dto.AlertNotificationPayload;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertStatus;
import com.example.demo.model.OutboxEvent;
import com.example.demo.model.OutboxEvent.OutboxStatus;
import com.example.demo.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.demo.model.OutboxEvent.OutboxStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    static final long DEVICE_ID = 1L;
    static final String METRIC_NAME = "temperature";

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private AlertNotificationClient alertNotificationClient;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxScheduler(outboxRepository, alertNotificationClient);
    }

    // ── Builder helpers ──────────────────────────────────────────────────────

    private OutboxEvent pendingEvent(long eventId, int attemptCount) {
        return pendingEventForPartition(eventId, attemptCount, DEVICE_ID, METRIC_NAME, "Temp Sensor");
    }

    private OutboxEvent pendingEventForPartition(long eventId, int attemptCount,
                                                  long deviceId, String metricName, String deviceName) {
        return OutboxEvent.builder()
                .id(eventId)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .metricName(metricName)
                .triggerValue(87.3)
                .threshold(80.0)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .alertOpenedAt(LocalDateTime.now().minusMinutes(5))
                .alertClosedAt(null)
                .status(PENDING)
                .attemptCount(attemptCount)
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .build();
    }

    /**
     * Stubs the two-step partition query for a single (deviceId, metricName) partition.
     * The sentinel is the first event in {@code partitionEvents}; all events in the list
     * are returned by {@code findAllPendingByPartition}.
     */
    private void stubPartition(long deviceId, String metricName, List<OutboxEvent> partitionEvents) {
        when(outboxRepository.claimPartitionSentinels(PENDING))
                .thenReturn(List.of(partitionEvents.get(0)));
        when(outboxRepository.findAllPendingByPartition(PENDING, deviceId, metricName))
                .thenReturn(partitionEvents);
    }

    // ── No pending events ────────────────────────────────────────────────────

    @Test
    void processPendingEvents_noPendingEvents_doesNothing() {
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of());

        scheduler.processPendingEvents();

        verifyNoInteractions(alertNotificationClient);
        verify(outboxRepository, never()).save(any());
    }

    // ── Successful delivery on first attempt ─────────────────────────────────

    @Test
    void processPendingEvents_successOnFirstAttempt_markedSent() {
        var event = pendingEvent(10L, 0);
        stubPartition(DEVICE_ID, METRIC_NAME, List.of(event));

        scheduler.processPendingEvents();

        var eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(eventCaptor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(eventCaptor.getValue().getLastAttemptAt()).isNotNull();

        var payloadCaptor = ArgumentCaptor.forClass(AlertNotificationPayload.class);
        verify(alertNotificationClient).send(payloadCaptor.capture());
        var payload = payloadCaptor.getValue();
        assertThat(payload.deviceName()).isEqualTo("Temp Sensor");
        assertThat(payload.metricName()).isEqualTo(METRIC_NAME);
        assertThat(payload.triggerValue()).isEqualTo(87.3);
        assertThat(payload.severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(payload.alertStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(payload.alertOpenedAt()).isNotNull();
        assertThat(payload.alertClosedAt()).isNull();
    }

    // ── Delivery failure — below max attempts → stays PENDING ────────────────

    @Test
    void processPendingEvents_firstAttemptFails_staysPending() {
        var event = pendingEvent(20L, 0);
        stubPartition(DEVICE_ID, METRIC_NAME, List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void processPendingEvents_secondAttemptFails_staysPending() {
        var event = pendingEvent(30L, 1); // already had 1 attempt
        stubPartition(DEVICE_ID, METRIC_NAME, List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
    }

    // ── Delivery failure — third attempt reaches MAX_ATTEMPTS → FAILED ────────

    @Test
    void processPendingEvents_thirdAttemptFails_markedFailed() {
        var event = pendingEvent(40L, 2); // already had 2 attempts
        stubPartition(DEVICE_ID, METRIC_NAME, List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(3);
    }

    // ── Success on third (final) attempt ─────────────────────────────────────

    @Test
    void processPendingEvents_successOnThirdAttempt_markedSent() {
        var event = pendingEvent(60L, 2); // already had 2 attempts
        stubPartition(DEVICE_ID, METRIC_NAME, List.of(event));

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(3);
    }

    // ── Cluster safety — SKIP LOCKED returns empty → node does nothing ────────

    /**
     * All partitions are already locked by another node.
     * {@code claimPartitionSentinels} returns empty — this node does nothing.
     */
    @Test
    void processPendingEvents_allPartitionsLockedByAnotherNode_doesNothing() {
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of());

        scheduler.processPendingEvents();

        verifyNoInteractions(alertNotificationClient);
        verify(outboxRepository, never()).save(any());
    }

    /**
     * Two nodes each claim a disjoint partition and deliver their own event exactly once.
     * Node-1 owns partition (deviceId=1, "temperature"), node-2 owns (deviceId=2, "temperature").
     */
    @Test
    void processPendingEvents_twoNodesProcessDisjointPartitions_eachDeliverOnce() {
        var eventA = pendingEventForPartition(100L, 0, 1L, METRIC_NAME, "Device-A");
        var eventB = pendingEventForPartition(101L, 0, 2L, METRIC_NAME, "Device-B");

        // node-1: claims partition for deviceId=1
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(eventA));
        when(outboxRepository.findAllPendingByPartition(PENDING, 1L, METRIC_NAME)).thenReturn(List.of(eventA));
        scheduler.processPendingEvents();

        // node-2: claims partition for deviceId=2
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(eventB));
        when(outboxRepository.findAllPendingByPartition(PENDING, 2L, METRIC_NAME)).thenReturn(List.of(eventB));
        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        var savedIds = captor.getAllValues().stream().map(OutboxEvent::getId).toList();
        assertThat(savedIds).containsExactlyInAnyOrder(100L, 101L);

        verify(alertNotificationClient, times(2)).send(any());
    }

    // ── Ordering within a partition ───────────────────────────────────────────

    /**
     * Three events in the same partition are delivered in strict {@code id ASC} order.
     * Each event has a unique deviceName so we can assert exact delivery sequence.
     */
    @Test
    void processPendingEvents_eventsInSamePartitionDeliveredInAscendingIdOrder() {
        var event1 = pendingEventForPartition(1L, 0, DEVICE_ID, METRIC_NAME, "Device-A");
        var event2 = pendingEventForPartition(2L, 0, DEVICE_ID, METRIC_NAME, "Device-B");
        var event3 = pendingEventForPartition(3L, 0, DEVICE_ID, METRIC_NAME, "Device-C");

        // Sentinel is the oldest row (id=1); all three are in the partition.
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(event1));
        when(outboxRepository.findAllPendingByPartition(PENDING, DEVICE_ID, METRIC_NAME))
                .thenReturn(List.of(event1, event2, event3));

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(AlertNotificationPayload.class);
        verify(alertNotificationClient, times(3)).send(captor.capture());
        var payloads = captor.getAllValues();
        assertThat(payloads).hasSize(3);
        assertThat(payloads.get(0).deviceName()).isEqualTo("Device-A");
        assertThat(payloads.get(1).deviceName()).isEqualTo("Device-B");
        assertThat(payloads.get(2).deviceName()).isEqualTo("Device-C");
    }

    // ── Fail-fast within a partition ──────────────────────────────────────────

    /**
     * When the first event in a partition fails delivery, all subsequent events
     * in that partition must NOT be sent — preserving ordering for the next tick.
     */
    @Test
    void processPendingEvents_firstEventInPartitionFails_subsequentEventsSkipped() {
        var event1 = pendingEventForPartition(10L, 0, DEVICE_ID, METRIC_NAME, "Device-A");
        var event2 = pendingEventForPartition(11L, 0, DEVICE_ID, METRIC_NAME, "Device-B");
        var event3 = pendingEventForPartition(12L, 0, DEVICE_ID, METRIC_NAME, "Device-C");

        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(event1));
        when(outboxRepository.findAllPendingByPartition(PENDING, DEVICE_ID, METRIC_NAME))
                .thenReturn(List.of(event1, event2, event3));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        // Only event1 was attempted — events 2 and 3 were never touched.
        verify(alertNotificationClient, times(1)).send(any());
        var saveCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getId()).isEqualTo(10L);
        assertThat(saveCaptor.getValue().getStatus()).isEqualTo(PENDING);
    }

    /**
     * When a middle event in a partition fails, all events after it are skipped.
     * Events before it (already delivered) are unaffected.
     */
    @Test
    void processPendingEvents_middleEventInPartitionFails_remainderSkipped() {
        var event1 = pendingEventForPartition(20L, 0, DEVICE_ID, METRIC_NAME, "Device-A");
        var event2 = pendingEventForPartition(21L, 0, DEVICE_ID, METRIC_NAME, "Device-B");
        var event3 = pendingEventForPartition(22L, 0, DEVICE_ID, METRIC_NAME, "Device-C");

        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(event1));
        when(outboxRepository.findAllPendingByPartition(PENDING, DEVICE_ID, METRIC_NAME))
                .thenReturn(List.of(event1, event2, event3));

        // event1 succeeds, event2 fails, event3 must be skipped.
        // We match on deviceName because the payload is a record whose timestamp fields
        // are captured at builder-time — using eq() on the full record would cause a
        // mismatch due to nanosecond differences between stub-time and execution-time.
        doAnswer(invocation -> {
            AlertNotificationPayload p = invocation.getArgument(0);
            if ("Device-B".equals(p.deviceName())) {
                throw new RuntimeException("timeout");
            }
            return null;
        }).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        // event1 (SENT) + event2 (PENDING, failed) saved; event3 never touched
        var saveCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(2)).save(saveCaptor.capture());
        var saved = saveCaptor.getAllValues();
        assertThat(saved.get(0).getId()).isEqualTo(20L);
        assertThat(saved.get(0).getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(saved.get(1).getId()).isEqualTo(21L);
        assertThat(saved.get(1).getStatus()).isEqualTo(PENDING);

        verify(alertNotificationClient, times(2)).send(any()); // event1 + event2; event3 never called
    }

    // ── Cross-partition independence ──────────────────────────────────────────

    /**
     * A delivery failure in partition A must not prevent events in partition B
     * from being delivered in the same tick.
     */
    @Test
    void processPendingEvents_failureInOnePartition_doesNotBlockOtherPartition() {
        var eventA = pendingEventForPartition(30L, 0, 1L, "temperature", "Device-A");
        var eventB = pendingEventForPartition(31L, 0, 2L, "humidity",    "Device-B");

        // Both partitions are claimed in the same tick (two sentinels returned)
        when(outboxRepository.claimPartitionSentinels(PENDING)).thenReturn(List.of(eventA, eventB));
        when(outboxRepository.findAllPendingByPartition(PENDING, 1L, "temperature")).thenReturn(List.of(eventA));
        when(outboxRepository.findAllPendingByPartition(PENDING, 2L, "humidity")).thenReturn(List.of(eventB));

        // Partition A always fails; partition B always succeeds.
        // Match on deviceName to avoid timestamp-equality issues with record payloads.
        doAnswer(invocation -> {
            AlertNotificationPayload p = invocation.getArgument(0);
            if ("Device-A".equals(p.deviceName())) {
                throw new RuntimeException("timeout");
            }
            return null;
        }).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var saveCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(2)).save(saveCaptor.capture());
        var saved = saveCaptor.getAllValues();

        var savedA = saved.stream().filter(e -> e.getId().equals(30L)).findFirst().orElseThrow();
        var savedB = saved.stream().filter(e -> e.getId().equals(31L)).findFirst().orElseThrow();

        assertThat(savedA.getStatus()).isEqualTo(PENDING);   // A failed → stays PENDING
        assertThat(savedB.getStatus()).isEqualTo(OutboxStatus.SENT); // B unaffected → SENT
    }
}
