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

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processPendingEvents() {
        var pendingEvents = outboxRepository.findByStatusForUpdate(PENDING);

        log.debug("Processing {} pending webhook outbox event(s).", pendingEvents.size());

        for (var event : pendingEvents) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxEvent event) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastAttemptAt(now());

        try {
            alertNotificationClient.send(AlertNotificationPayload.from(event));

            event.setStatus(SENT);
            log.info("Notification delivered for metric={}, device='{}' (attempt {}).",
                    event.getMetricName(), event.getDeviceName(), event.getAttemptCount());
        } catch (Exception ex) {
            log.warn("Notification delivery failed for metric={}, device='{}' (attempt {}/{}): {}",
                    event.getMetricName(), event.getDeviceName(),
                    event.getAttemptCount(), MAX_ATTEMPTS, ex.getMessage());

            if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.setStatus(FAILED);
                log.error("Notification permanently failed for metric={}, device='{}' after {} attempts.",
                        event.getMetricName(), event.getDeviceName(), MAX_ATTEMPTS);
            }
            // else status stays PENDING — will be retried on the next scheduler tick
        }

        outboxRepository.save(event);
    }
}
