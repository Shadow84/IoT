package com.example.demo.controller;

import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.UpdateAlertRuleRequest;
import com.example.demo.model.AlertOperator;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.DeviceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AlertRuleControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long deviceId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        var deviceRequest = new CreateDeviceRequest("Test Device", DeviceType.SENSOR, "Lab", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceRequest)))
                .andReturn();
        deviceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void createRule_validRequest_returns201() throws Exception {
        var request = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.metricName").value("temperature"))
                .andExpect(jsonPath("$.operator").value("GREATER_THAN"))
                .andExpect(jsonPath("$.threshold").value(80.0))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void createRule_missingDeviceId_returns400() throws Exception {
        var body = """
                {"metricName":"temperature","operator":"GREATER_THAN","threshold":80.0,"severity":"HIGH","enabled":true}
                """;
        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_unknownDevice_returns404() throws Exception {
        var request = new CreateAlertRuleRequest(
                99999L, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRules_returnsAll() throws Exception {
        var request = new CreateAlertRuleRequest(
                deviceId, "humidity", AlertOperator.LESS_THAN, 30.0, AlertSeverity.MEDIUM, true);
        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metricName").value("humidity"));
    }

    @Test
    void getRule_missingId_returns404() throws Exception {
        mockMvc.perform(get("/api/alert-rules/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRule_patchesSeverity() throws Exception {
        var createRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);
        var result = mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();
        var ruleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        var update = new UpdateAlertRuleRequest(null, null, null, AlertSeverity.CRITICAL, null);
        mockMvc.perform(put("/api/alert-rules/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    void deleteRule_removesRule() throws Exception {
        var createRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);
        var result = mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();
        var ruleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/alert-rules/" + ruleId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alert-rules/" + ruleId))
                .andExpect(status().isNotFound());
    }

    @Test
    void disableRule_happyPath_returns204AndRuleIsDisabled() throws Exception {
        var createRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);
        var result = mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();
        var ruleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/alert-rules/" + ruleId + "/disable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alert-rules/" + ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enableRule_happyPath_returns204AndRuleIsEnabled() throws Exception {
        var createRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, false);
        var result = mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn();
        var ruleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/alert-rules/" + ruleId + "/enable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alert-rules/" + ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void disableRule_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/api/alert-rules/99999/disable"))
                .andExpect(status().isNotFound());
    }

    @Test
    void enableRule_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/api/alert-rules/99999/enable"))
                .andExpect(status().isNotFound());
    }
}
