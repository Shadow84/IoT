package com.example.demo.service;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.DeviceResponse;
import com.example.demo.dto.PagedResponse;
import com.example.demo.dto.UpdateDeviceRequest;
import com.example.demo.exception.DeviceUnactivatedException;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceStatus;
import com.example.demo.repository.DeviceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.demo.model.DeviceStatus.ACTIVE;
import static com.example.demo.model.DeviceStatus.INACTIVE;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceMapper deviceMapper;

    @Transactional
    public DeviceResponse register(CreateDeviceRequest request) {
        var device = deviceMapper.toDevice(request);

        device.setStatus(DeviceStatus.ACTIVE);
        var saved = deviceRepository.save(device);
        log.info("Registered device id={}, name='{}'.", saved.getId(), saved.getName());

        return deviceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DeviceResponse> listAll(Pageable pageable) {
        var page = deviceRepository.findAll(pageable)
                .map(deviceMapper::toResponse);

        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public DeviceResponse getById(Long id) {
        return deviceMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public DeviceResponse update(Long id, UpdateDeviceRequest request) {
        var device = findOrThrow(id);
        deviceMapper.updateDevice(request, device);
        var saved = deviceRepository.save(device);
        log.info("Updated device id={}.", saved.getId());

        return deviceMapper.toResponse(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        var device = findOrThrow(id);
        device.setStatus(INACTIVE);
        deviceRepository.save(device);
        log.info("Deactivated device id={}.", id);
    }

    @Transactional
    public void activate(Long id) {
        var device = findOrThrow(id);
        device.setStatus(ACTIVE);
        deviceRepository.save(device);
        log.info("Activated device id={}.", id);
    }

    public Device findOrThrow(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
    }

    public Device findActiveOrThrow(Long id) {
        var device = findOrThrow(id);
        if (device.getStatus() == INACTIVE) {
            log.warn("Device id={} is inactive. Metric ingestion rejected.", id);
            throw new DeviceUnactivatedException(id);
        }
        return device;
    }
}
