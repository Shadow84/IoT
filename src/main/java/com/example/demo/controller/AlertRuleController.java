package com.example.demo.controller;

import com.example.demo.dto.AlertRuleResponse;
import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.service.AlertRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody CreateAlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRuleService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> listAll() {
        return ResponseEntity.ok(alertRuleService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(alertRuleService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> update(@PathVariable Long id,
                                                     @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(alertRuleService.update(id, request));
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        alertRuleService.enable(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        alertRuleService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
