package com.example.demo.dto;

import com.example.demo.model.DeviceStatus;
import com.example.demo.model.DeviceType;

import java.util.Map;

public record DeviceResponse(
        Long id,
        String name,
        DeviceType type,
        String location,
        Map<String, String> extraAttributes,
        DeviceStatus status
) {}
