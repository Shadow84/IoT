package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "alert")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"device", "rule"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private Double triggerValue;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    @Column
    private LocalDateTime closedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;
}
