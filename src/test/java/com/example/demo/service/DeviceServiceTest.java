package com.example.demo.service;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.DeviceResponse;
import com.example.demo.dto.UpdateDeviceRequest;
import com.example.demo.exception.DeviceUnactivatedException;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceStatus;
import com.example.demo.model.DeviceType;
import com.example.demo.repository.DeviceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceMapper deviceMapper;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceRepository, deviceMapper);
    }

    @Test
    void register_validRequest_savesAndReturnsResponse() {
        var request = new CreateDeviceRequest(
                "Warehouse-A Temp Sensor", DeviceType.SENSOR, "Building 3",
                Map.of("serialNumber", "SN-001"));

        var device = Device.builder()
                .name("Warehouse-A Temp Sensor")
                .type(DeviceType.SENSOR)
                .location("Building 3")
                .extraAttributes(Map.of("serialNumber", "SN-001"))
                .build();

        var saved = Device.builder()
                .id(1L)
                .name("Warehouse-A Temp Sensor")
                .type(DeviceType.SENSOR)
                .location("Building 3")
                .extraAttributes(Map.of("serialNumber", "SN-001"))
                .status(DeviceStatus.ACTIVE)
                .build();

        var expected = new DeviceResponse(1L, "Warehouse-A Temp Sensor", DeviceType.SENSOR,
                "Building 3", Map.of("serialNumber", "SN-001"), DeviceStatus.ACTIVE);

        when(deviceMapper.toDevice(request)).thenReturn(device);
        when(deviceRepository.save(any(Device.class))).thenReturn(saved);
        when(deviceMapper.toResponse(saved)).thenReturn(expected);

        var response = deviceService.register(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Warehouse-A Temp Sensor");
        assertThat(response.type()).isEqualTo(DeviceType.SENSOR);
        assertThat(response.location()).isEqualTo("Building 3");
        assertThat(response.status()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(response.extraAttributes()).containsEntry("serialNumber", "SN-001");
    }

    @Test
    void register_nullExtraAttributes_usesEmptyMap() {
        var request = new CreateDeviceRequest("Sensor", DeviceType.SENSOR, "Room 1", null);

        var device = Device.builder()
                .name("Sensor").type(DeviceType.SENSOR).location("Room 1")
                .extraAttributes(new HashMap<>()).build();

        var saved = Device.builder().id(2L).name("Sensor").type(DeviceType.SENSOR)
                .location("Room 1").extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();

        var expected = new DeviceResponse(2L, "Sensor", DeviceType.SENSOR,
                "Room 1", new HashMap<>(), DeviceStatus.ACTIVE);

        when(deviceMapper.toDevice(request)).thenReturn(device);
        when(deviceRepository.save(any(Device.class))).thenReturn(saved);
        when(deviceMapper.toResponse(saved)).thenReturn(expected);

        var response = deviceService.register(request);

        assertThat(response.extraAttributes()).isEmpty();
    }

    @Test
    void listAll_returnsPagedDevices() {
        var d1 = Device.builder().id(1L).name("D1").type(DeviceType.SENSOR).location("L1")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();
        var d2 = Device.builder().id(2L).name("D2").type(DeviceType.GATEWAY).location("L2")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.INACTIVE).build();

        var r1 = new DeviceResponse(1L, "D1", DeviceType.SENSOR, "L1", new HashMap<>(), DeviceStatus.ACTIVE);
        var r2 = new DeviceResponse(2L, "D2", DeviceType.GATEWAY, "L2", new HashMap<>(), DeviceStatus.INACTIVE);

        var pageable = PageRequest.of(0, 20);
        var devicePage = new PageImpl<>(List.of(d1, d2), pageable, 2);

        when(deviceRepository.findAll(pageable)).thenReturn(devicePage);
        when(deviceMapper.toResponse(d1)).thenReturn(r1);
        when(deviceMapper.toResponse(d2)).thenReturn(r2);

        var result = deviceService.listAll(pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).name()).isEqualTo("D1");
        assertThat(result.content().get(1).name()).isEqualTo("D2");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    void getById_existingId_returnsResponse() {
        var device = Device.builder().id(1L).name("D1").type(DeviceType.SENSOR).location("L1")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();
        var expected = new DeviceResponse(1L, "D1", DeviceType.SENSOR, "L1", new HashMap<>(), DeviceStatus.ACTIVE);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceMapper.toResponse(device)).thenReturn(expected);

        var response = deviceService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("D1");
    }

    @Test
    void getById_missingId_throwsEntityNotFoundException() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_patchesOnlyProvidedFields() {
        var device = Device.builder().id(1L).name("OldName").type(DeviceType.SENSOR).location("OldLoc")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();
        var expected = new DeviceResponse(1L, "NewName", DeviceType.SENSOR, "OldLoc",
                new HashMap<>(), DeviceStatus.ACTIVE);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenReturn(device);
        when(deviceMapper.toResponse(device)).thenReturn(expected);

        var request = new UpdateDeviceRequest("NewName", null, null, null);
        var response = deviceService.update(1L, request);

        verify(deviceMapper).updateDevice(request, device);
        assertThat(response.name()).isEqualTo("NewName");
    }

    @Test
    void deactivate_setsStatusToInactive() {
        var device = Device.builder().id(1L).name("D1").type(DeviceType.SENSOR).location("L1")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenReturn(device);

        deviceService.deactivate(1L);

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.INACTIVE);
        verify(deviceRepository).save(device);
    }

    @Test
    void deactivate_missingId_throwsEntityNotFoundException() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.deactivate(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findActiveOrThrow_activeDevice_returnsDevice() {
        var device = Device.builder().id(1L).name("D1").type(DeviceType.SENSOR).location("L1")
                .extraAttributes(new HashMap<>()).status(DeviceStatus.ACTIVE).build();
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        var result = deviceService.findActiveOrThrow(1L);

        assertThat(result).isSameAs(device);
    }

    @Test
    void findActiveOrThrow_inactiveDevice_throwsDeviceUnactivatedException() {
        var device = Device.builder().id(3L).name("Old Sensor").type(DeviceType.SENSOR)
                .location("Storage").extraAttributes(new HashMap<>())
                .status(DeviceStatus.INACTIVE).build();
        when(deviceRepository.findById(3L)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.findActiveOrThrow(3L))
                .isInstanceOf(DeviceUnactivatedException.class)
                .hasMessageContaining("INACTIVE");
    }
}
