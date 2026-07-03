package com.example.demo.dto;

import com.example.demo.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateDeviceRequest(
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "type must not be null") DeviceType type,
        @NotBlank(message = "location must not be blank") String location,
        Map<String, String> extraAttributes
) {}
