package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // ── Denormalized alert snapshot ──────────────────────────────────────────
    // All fields are captured at creation time so the scheduler never needs
    // a secondary lookup (no N+1, no FK dependency on the Alert table).
    // deviceId is a plain copy (no FK) — together with metricName it forms
    // the partition key for ordered multi-node outbox delivery.

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private Double triggerValue;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus alertStatus;

    @Column(nullable = false)
    private LocalDateTime alertOpenedAt;

    @Column
    private LocalDateTime alertClosedAt;

    // ── Outbox delivery fields ───────────────────────────────────────────────

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastAttemptAt;

    public enum OutboxStatus {
        PENDING, SENT, FAILED
    }
}
