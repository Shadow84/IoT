package com.example.demo.mapper;

import com.example.demo.dto.AlertRuleResponse;
import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.model.AlertRule;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AlertRuleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "device", ignore = true)
    AlertRule toRule(CreateAlertRuleRequest request);

    @Mapping(source = "device.id", target = "deviceId")
    @Mapping(source = "device.name", target = "deviceName")
    AlertRuleResponse toResponse(AlertRule rule);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "device", ignore = true)
    void updateRule(UpdateAlertRuleRequest request, @MappingTarget AlertRule rule);
}
