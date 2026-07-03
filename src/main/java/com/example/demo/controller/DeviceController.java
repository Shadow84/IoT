package com.example.demo.controller;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.DevicePageRequest;
import com.example.demo.dto.DeviceResponse;
import com.example.demo.dto.PagedResponse;
import com.example.demo.dto.UpdateDeviceRequest;
import com.example.demo.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody CreateDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.register(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<DeviceResponse>> listAll(
            @Valid @ModelAttribute DevicePageRequest pageRequest) {
        return ResponseEntity.ok(deviceService.listAll(pageRequest.toPageable()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateDeviceRequest request) {
        return ResponseEntity.ok(deviceService.update(id, request));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        deviceService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        deviceService.activate(id);
        return ResponseEntity.noContent().build();
    }
}
