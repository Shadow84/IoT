package com.example.demo.service;

import com.example.demo.dto.AlertInstanceResponse;
import com.example.demo.dto.AlertPageRequest;
import com.example.demo.dto.PagedResponse;
import com.example.demo.repository.AlertRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public PagedResponse<AlertInstanceResponse> listAll(AlertPageRequest pageRequest) {
        var page = alertRepository.findAllWithDeviceAndRule(pageRequest.toPageable())
                .map(AlertInstanceResponse::from);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public AlertInstanceResponse getById(Long id) {
        var alert = alertRepository.findByIdWithDeviceAndRule(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert instance not found: " + id));
        return AlertInstanceResponse.from(alert);
    }
}
