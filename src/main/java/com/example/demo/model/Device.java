package com.example.demo.model;

import com.example.demo.config.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceType type;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, columnDefinition = "CLOB")
    @Convert(converter = JsonAttributeConverter.class)
    @Builder.Default
    private Map<String, String> extraAttributes = new HashMap<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.ACTIVE;
}
