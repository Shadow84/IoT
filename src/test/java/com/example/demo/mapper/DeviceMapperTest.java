package com.example.demo.mapper;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.DeviceResponse;
import com.example.demo.dto.UpdateDeviceRequest;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceStatus;
import com.example.demo.model.DeviceType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMapperTest {

    private final DeviceMapper mapper = Mappers.getMapper(DeviceMapper.class);

    // -------------------------------------------------------------------------
    // toDevice
    // -------------------------------------------------------------------------

    @Test
    void toDevice_mapsAllFields() {
        var request = new CreateDeviceRequest(
                "Temp Sensor", DeviceType.SENSOR, "Room 101",
                Map.of("firmware", "v1.2"));

        var device = mapper.toDevice(request);

        assertThat(device.getId()).isNull();
        assertThat(device.getName()).isEqualTo("Temp Sensor");
        assertThat(device.getType()).isEqualTo(DeviceType.SENSOR);
        assertThat(device.getLocation()).isEqualTo("Room 101");
        assertThat(device.getExtraAttributes()).containsEntry("firmware", "v1.2");
    }

    @Test
    void toDevice_nullExtraAttributes_defaultsToEmptyMap() {
        var request = new CreateDeviceRequest(
                "Gateway A", DeviceType.GATEWAY, "Lobby", null);

        var device = mapper.toDevice(request);

        assertThat(device.getExtraAttributes()).isNotNull().isEmpty();
    }

    @Test
    void toDevice_statusIsNotExplicitlySetByMapper() {
        var request = new CreateDeviceRequest(
                "Controller 1", DeviceType.CONTROLLER, "Server Room", null);

        var device = mapper.toDevice(request);

        // id is never assigned by the mapper (database-generated)
        assertThat(device.getId()).isNull();
        // @Builder.Default initialises status to ACTIVE; the mapper does not override it
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // toResponse
    // -------------------------------------------------------------------------

    @Test
    void toResponse_mapsAllFields() {
        var device = Device.builder()
                .id(7L)
                .name("Humidity Sensor")
                .type(DeviceType.SENSOR)
                .location("Greenhouse")
                .extraAttributes(Map.of("zone", "B"))
                .status(DeviceStatus.ACTIVE)
                .build();

        var response = mapper.toResponse(device);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Humidity Sensor");
        assertThat(response.type()).isEqualTo(DeviceType.SENSOR);
        assertThat(response.location()).isEqualTo("Greenhouse");
        assertThat(response.extraAttributes()).containsEntry("zone", "B");
        assertThat(response.status()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void toResponse_inactiveDevice_preservesStatus() {
        var device = Device.builder()
                .id(3L)
                .name("Old Sensor")
                .type(DeviceType.SENSOR)
                .location("Storage")
                .status(DeviceStatus.INACTIVE)
                .build();

        var response = mapper.toResponse(device);

        assertThat(response.status()).isEqualTo(DeviceStatus.INACTIVE);
    }

    // -------------------------------------------------------------------------
    // updateDevice (partial patch)
    // -------------------------------------------------------------------------

    @Test
    void updateDevice_appliesNonNullFields() {
        var device = Device.builder()
                .id(2L)
                .name("Old Name")
                .type(DeviceType.SENSOR)
                .location("Old Location")
                .status(DeviceStatus.ACTIVE)
                .build();

        var request = new UpdateDeviceRequest("New Name", null, "New Location", null);

        mapper.updateDevice(request, device);

        assertThat(device.getName()).isEqualTo("New Name");
        assertThat(device.getType()).isEqualTo(DeviceType.SENSOR);    // unchanged
        assertThat(device.getLocation()).isEqualTo("New Location");
        assertThat(device.getId()).isEqualTo(2L);                     // untouched
    }

    @Test
    void updateDevice_allNullRequest_leavesDeviceUnchanged() {
        var device = Device.builder()
                .id(5L)
                .name("Unchanged")
                .type(DeviceType.CONTROLLER)
                .location("Lab")
                .extraAttributes(Map.of("key", "value"))
                .status(DeviceStatus.ACTIVE)
                .build();

        var request = new UpdateDeviceRequest(null, null, null, null);

        mapper.updateDevice(request, device);

        assertThat(device.getName()).isEqualTo("Unchanged");
        assertThat(device.getType()).isEqualTo(DeviceType.CONTROLLER);
        assertThat(device.getLocation()).isEqualTo("Lab");
        assertThat(device.getExtraAttributes()).containsEntry("key", "value");
    }

    @Test
    void updateDevice_doesNotResetExtraAttributesToEmptyMap() {
        var device = Device.builder()
                .id(6L)
                .name("Sensor X")
                .type(DeviceType.SENSOR)
                .location("Zone A")
                .extraAttributes(Map.of("serial", "SN-001"))
                .status(DeviceStatus.ACTIVE)
                .build();

        // only name is updated — extraAttributes is null in the request
        var request = new UpdateDeviceRequest("Sensor X v2", null, null, null);

        mapper.updateDevice(request, device);

        assertThat(device.getName()).isEqualTo("Sensor X v2");
        // extraAttributes must NOT have been wiped by the @AfterMapping hook
        assertThat(device.getExtraAttributes()).containsEntry("serial", "SN-001");
    }
}
