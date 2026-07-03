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

@Entity
@Table(name = "alert_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "device")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertOperator operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
