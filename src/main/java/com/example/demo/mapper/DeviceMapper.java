package com.example.demo.mapper;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.DeviceResponse;
import com.example.demo.dto.UpdateDeviceRequest;
import com.example.demo.model.Device;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.HashMap;

@Mapper(componentModel = "spring")
public interface DeviceMapper {

    /**
     * Maps a creation request to a new Device entity.
     * {@code id} and {@code status} are intentionally left unset here —
     * the service sets {@code status = ACTIVE} before saving.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    Device toDevice(CreateDeviceRequest request);

    /** Ensures extraAttributes is never null after mapping from a CreateDeviceRequest. */
    @AfterMapping
    default void defaultExtraAttributes(@MappingTarget Device device) {
        if (device.getExtraAttributes() == null) {
            device.setExtraAttributes(new HashMap<>());
        }
    }

    /** Maps a Device entity to its response DTO. */
    DeviceResponse toResponse(Device device);

    /**
     * Applies a partial update: only non-null fields from the request
     * are copied onto the existing managed entity.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDevice(UpdateDeviceRequest request, @MappingTarget Device device);
}
