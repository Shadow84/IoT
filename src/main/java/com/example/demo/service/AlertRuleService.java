package com.example.demo.service;

import com.example.demo.dto.AlertRuleResponse;
import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.mapper.AlertRuleMapper;
import com.example.demo.model.AlertRule;
import com.example.demo.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleMapper alertRuleMapper;
    private final DeviceService deviceService;

    @Transactional
    public AlertRuleResponse create(CreateAlertRuleRequest request) {
        var device = deviceService.findOrThrow(request.deviceId());

        var rule = alertRuleMapper.toRule(request);
        rule.setDevice(device);
        var saved = alertRuleRepository.save(rule);
        log.info("Created alert rule id={} for device id={}.", saved.getId(), device.getId());

        return alertRuleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listAll() {
        return alertRuleRepository.findAll()
                .stream()
                .map(alertRuleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertRuleResponse getById(Long id) {
        return alertRuleMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public AlertRuleResponse update(Long id, UpdateAlertRuleRequest request) {
        var rule = findOrThrow(id);
        alertRuleMapper.updateRule(request, rule);
        var saved = alertRuleRepository.save(rule);
        log.info("Updated alert rule id={}.", saved.getId());
        return alertRuleMapper.toResponse(saved);
    }

    @Transactional
    public void enable(Long id) {
        var rule = findOrThrow(id);
        rule.setEnabled(true);
        alertRuleRepository.save(rule);
        log.info("Enabled alert rule id={}.", id);
    }

    @Transactional
    public void disable(Long id) {
        var rule = findOrThrow(id);
        rule.setEnabled(false);
        alertRuleRepository.save(rule);
        log.info("Disabled alert rule id={}.", id);
    }

    @Transactional
    public void delete(Long id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new EntityNotFoundException("Alert rule not found: " + id);
        }
        alertRuleRepository.deleteById(id);
        log.info("Deleted alert rule id={}.", id);
    }

    private AlertRule findOrThrow(Long id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + id));
    }
}
