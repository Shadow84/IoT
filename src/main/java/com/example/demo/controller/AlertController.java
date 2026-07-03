package com.example.demo.controller;

import com.example.demo.dto.AlertInstanceResponse;
import com.example.demo.dto.AlertPageRequest;
import com.example.demo.dto.PagedResponse;
import com.example.demo.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<PagedResponse<AlertInstanceResponse>> listAll(
            @Valid @ModelAttribute AlertPageRequest pageRequest) {
        return ResponseEntity.ok(alertService.listAll(pageRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertInstanceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.getById(id));
    }
}
