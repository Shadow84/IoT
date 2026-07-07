package com.example.demo.service;

import com.example.demo.client.AlertNotificationClient;
import com.example.demo.dto.AlertNotificationPayload;
import com.example.demo.model.OutboxEvent;
import com.example.demo.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.example.demo.model.OutboxEvent.OutboxStatus.FAILED;
import static com.example.demo.model.OutboxEvent.OutboxStatus.PENDING;
import static com.example.demo.model.OutboxEvent.OutboxStatus.SENT;
import static java.time.LocalDateTime.now;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    static final int MAX_ATTEMPTS = 3;

    private final OutboxEventRepository outboxRepository;
    private final AlertNotificationClient alertNotificationClient;

    /**
     * Processes pending outbox events in per-{@code (deviceId, metricName)} partition order.
     *
     * <h3>Ordering guarantee</h3>
     * <p>Events within the same {@code (deviceId, metricName)} partition are always
     * delivered in {@code id ASC} (insertion) order — e.g. an {@code OPEN} event is
     * always delivered before the corresponding {@code CLOSED} event for the same alert.
     *
     * <h3>Multi-node cluster safety</h3>
     * <ol>
     *   <li>{@code claimPartitionSentinels} locks the <em>oldest</em> {@code PENDING} row
     *       in each partition using {@code FOR UPDATE SKIP LOCKED}.  A competing node will
     *       see that sentinel row as locked and skip the whole partition for this tick.</li>
     *   <li>Within a claimed partition all events are loaded in {@code id ASC} order and
     *       delivered sequentially.</li>
     *   <li>If delivery of an event fails, processing of that partition stops immediately
     *       (fail-fast).  Subsequent events in the same partition are not touched — their
     *       relative order is preserved for the next tick.</li>
     *   <li>A failure in one partition never blocks delivery in other partitions.</li>
     * </ol>
     */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processPendingEvents() {
        var sentinels = outboxRepository.claimPartitionSentinels(PENDING);

        if (sentinels.isEmpty()) {
            log.debug("No pending outbox partitions to process.");
            return;
        }

        log.debug("Claimed {} pending outbox partition(s).", sentinels.size());

        sentinels.forEach(sentinel -> processPartition(sentinel.getDeviceId(), sentinel.getMetricName()));
    }

    private void processPartition(Long deviceId, String metricName) {
        var events = outboxRepository.findAllPendingByPartition(PENDING, deviceId, metricName);

        log.debug("Processing partition deviceId={}, metric='{}': {} event(s).", deviceId, metricName, events.size());

        for (var event : events) {
            var delivered = processEvent(event);
            if (!delivered) {
                // Fail-fast: stop this partition to preserve ordering — later events in
                // this partition must not be delivered before the current one succeeds.
                log.debug("Stopping partition deviceId={}, metric='{}' after delivery failure on event id={}.",
                        deviceId, metricName, event.getId());
                return;
            }
        }
    }

    /**
     * Attempts to deliver a single event.
     *
     * @return {@code true} if delivery succeeded (event marked {@code SENT}),
     *         {@code false} if delivery failed (event stays {@code PENDING} or is marked {@code FAILED}).
     */
    private boolean processEvent(OutboxEvent event) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastAttemptAt(now());

        try {
            alertNotificationClient.send(AlertNotificationPayload.from(event));

            event.setStatus(SENT);
            outboxRepository.save(event);
            log.info("Notification delivered for metric='{}', device='{}' (attempt {}).",
                    event.getMetricName(), event.getDeviceName(), event.getAttemptCount());
            return true;

        } catch (Exception ex) {
            log.warn("Notification delivery failed for metric='{}', device='{}' (attempt {}/{}): {}",
                    event.getMetricName(), event.getDeviceName(),
                    event.getAttemptCount(), MAX_ATTEMPTS, ex.getMessage());

            if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.setStatus(FAILED);
                log.error("Notification permanently failed for metric='{}', device='{}' after {} attempts.",
                        event.getMetricName(), event.getDeviceName(), MAX_ATTEMPTS);
            }
            // else status stays PENDING — will be retried on the next scheduler tick
            outboxRepository.save(event);
            return false;
        }
    }
}
