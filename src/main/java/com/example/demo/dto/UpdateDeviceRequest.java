package com.example.demo.dto;

import com.example.demo.model.DeviceType;

import java.util.Map;

public record UpdateDeviceRequest(
        String name,
        DeviceType type,
        String location,
        Map<String, String> extraAttributes
) {}
