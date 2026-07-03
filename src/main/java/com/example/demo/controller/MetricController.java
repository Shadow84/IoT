package com.example.demo.controller;

import com.example.demo.dto.CreateMetricRequest;
import com.example.demo.service.MetricService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @PostMapping
    public ResponseEntity<Void> ingest(@Valid @RequestBody CreateMetricRequest request) {
        metricService.ingest(request);
        return ResponseEntity.noContent().build();
    }
}
