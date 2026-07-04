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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private AlertNotificationClient alertNotificationClient;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxScheduler(outboxRepository, alertNotificationClient);
    }

    private OutboxEvent pendingEvent(long eventId, int attemptCount) {
        return OutboxEvent.builder()
                .id(eventId)
                .deviceName("Temp Sensor")
                .metricName("temperature")
                .triggerValue(87.3)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .alertOpenedAt(LocalDateTime.now().minusMinutes(5))
                .alertClosedAt(null)
                .status(PENDING)
                .attemptCount(attemptCount)
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .build();
    }

    // -----------------------------------------------------------------------
    // No pending events
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_noPendingEvents_doesNothing() {
        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of());

        scheduler.processPendingEvents();

        verify(alertNotificationClient, never()).send(any());
        verify(outboxRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Successful delivery on first attempt
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_successOnFirstAttempt_markedSent() {
        var event = pendingEvent(10L, 0);

        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(event));

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
        assertThat(payload.metricName()).isEqualTo("temperature");
        assertThat(payload.triggerValue()).isEqualTo(87.3);
        assertThat(payload.severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(payload.alertStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(payload.alertOpenedAt()).isNotNull();
        assertThat(payload.alertClosedAt()).isNull();
    }

    // -----------------------------------------------------------------------
    // Delivery failure — below max attempts → stays PENDING
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_firstAttemptFails_staysPending() {
        var event = pendingEvent(20L, 0);

        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Delivery failure — second attempt still below max → stays PENDING
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_secondAttemptFails_staysPending() {
        var event = pendingEvent(30L, 1); // already had 1 attempt

        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Delivery failure — third attempt reaches MAX_ATTEMPTS → permanently FAILED
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_thirdAttemptFails_markedFailed() {
        var event = pendingEvent(40L, 2); // already had 2 attempts

        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(event));
        doThrow(new RuntimeException("timeout")).when(alertNotificationClient).send(any());

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Success on third (final) attempt
    // -----------------------------------------------------------------------

    @Test
    void processPendingEvents_successOnThirdAttempt_markedSent() {
        var event = pendingEvent(60L, 2); // already had 2 attempts

        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(event));

        scheduler.processPendingEvents();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Cluster safety — second node sees empty list when all rows are locked
    // -----------------------------------------------------------------------

    /**
     * Simulates a second cluster node whose FOR UPDATE SKIP LOCKED query returns
     * an empty list because another node already holds row-level locks on every
     * PENDING event.  The second node must not call the notification client at all
     * and must not issue any spurious save().
     *
     * <p>In production the DB enforces the skip-locked semantics; here we model it
     * by stubbing {@code findByStatusForUpdate} to return an empty list, which is
     * exactly what Hibernate/PostgreSQL will hand back to the second node.
     */
    @Test
    void processPendingEvents_allRowsLockedByAnotherNode_doesNothing() {
        // All PENDING rows are already locked by node-1 → node-2 gets empty list.
        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of());

        scheduler.processPendingEvents();

        verifyNoInteractions(alertNotificationClient);
        verify(outboxRepository, never()).save(any());
    }

    /**
     * Simulates two cluster nodes where node-1 holds a lock on event-A and
     * node-2 holds a lock on event-B.  Each node processes only its own event,
     * proving there is no cross-node duplication.
     */
    @Test
    void processPendingEvents_twoNodesProcessDisjointSubsets_eachDeliverOnce() {
        var eventA = pendingEvent(100L, 0); // "claimed" by node-1
        var eventB = pendingEvent(101L, 0); // "claimed" by node-2

        // node-1 scheduler instance
        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(eventA));
        scheduler.processPendingEvents();

        // node-2 scheduler instance (same mock, different stub return)
        when(outboxRepository.findByStatusForUpdate(PENDING)).thenReturn(List.of(eventB));
        scheduler.processPendingEvents();

        // Each event was saved exactly once — no duplicate processing.
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        var savedIds = captor.getAllValues().stream().map(OutboxEvent::getId).toList();
        assertThat(savedIds).containsExactlyInAnyOrder(100L, 101L);

        // Webhook was called exactly twice — once per event, never for the same event twice.
        verify(alertNotificationClient, org.mockito.Mockito.times(2)).send(any());
    }

    // -----------------------------------------------------------------------
    // Ordering — events are processed in ascending id order
    // -----------------------------------------------------------------------

    /**
     * The repository query uses {@code ORDER BY e.id ASC}.  This test verifies
     * that the scheduler forwards payloads to the notification client in the
     * exact order the repository returns them (ascending id).
     *
     * <p>Each event is given a unique {@code deviceName} so we can assert that
     * the first payload corresponds to the lowest id, and so on — proving the
     * scheduler does not reorder the list it receives from the repository.
     */
    @Test
    void processPendingEvents_eventsDeliveredInAscendingIdOrder() {
        var event1 = buildPendingEvent(1L, "Device-A");
        var event2 = buildPendingEvent(2L, "Device-B");
        var event3 = buildPendingEvent(3L, "Device-C");

        // Repository contract: returns events sorted by id ASC (ORDER BY e.id ASC).
        when(outboxRepository.findByStatusForUpdate(PENDING))
                .thenReturn(List.of(event1, event2, event3));

        scheduler.processPendingEvents();

        // Capture all three payloads in the order the client received them.
        var captor = ArgumentCaptor.forClass(AlertNotificationPayload.class);
        verify(alertNotificationClient, org.mockito.Mockito.times(3)).send(captor.capture());

        var payloads = captor.getAllValues();
        assertThat(payloads).hasSize(3);
        // Payloads must arrive in id-ascending order: Device-A → Device-B → Device-C.
        assertThat(payloads.get(0).deviceName()).isEqualTo("Device-A");
        assertThat(payloads.get(1).deviceName()).isEqualTo("Device-B");
        assertThat(payloads.get(2).deviceName()).isEqualTo("Device-C");
    }

    private OutboxEvent buildPendingEvent(long eventId, String deviceName) {
        return OutboxEvent.builder()
                .id(eventId)
                .deviceName(deviceName)
                .metricName("temperature")
                .triggerValue(87.3)
                .threshold(80.0)
                .severity(AlertSeverity.HIGH)
                .alertStatus(AlertStatus.OPEN)
                .alertOpenedAt(LocalDateTime.now().minusMinutes(5))
                .alertClosedAt(null)
                .status(PENDING)
                .attemptCount(0)
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .build();
    }
}
