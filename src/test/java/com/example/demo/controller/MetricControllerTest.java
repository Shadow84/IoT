package com.example.demo.controller;

import com.example.demo.dto.CreateAlertRuleRequest;
import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.CreateMetricRequest;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MetricControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long deviceId;
    private static final LocalDateTime FIXED_TS = LocalDateTime.of(2026, 7, 3, 10, 0, 0);

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        var deviceRequest = new CreateDeviceRequest("Warehouse-A Temp Sensor", DeviceType.SENSOR, "Building 3", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceRequest)))
                .andReturn();
        deviceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void ingestMetric_noRule_returns204() throws Exception {
        var request = new CreateMetricRequest(deviceId, "temperature", 65.0, FIXED_TS);
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void ingestMetric_violatesRule_opensAlert() throws Exception {
        var ruleRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);
        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleRequest)))
                .andExpect(status().isCreated());

        var metricRequest = new CreateMetricRequest(deviceId, "temperature", 87.3, FIXED_TS);
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metricRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].triggerValue").value(87.3))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    @Test
    void ingestMetric_thenRecovers_closesAlert() throws Exception {
        var ruleRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.HIGH, true);
        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleRequest)))
                .andExpect(status().isCreated());

        // Trigger alert
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateMetricRequest(deviceId, "temperature", 87.3, FIXED_TS))))
                .andExpect(status().isNoContent());

        // Recovery reading
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateMetricRequest(deviceId, "temperature", 75.0, FIXED_TS.plusMinutes(5)))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.content[0].closedAt").isNotEmpty());
    }

    @Test
    void ingestMetric_missingDeviceId_returns400() throws Exception {
        var body = """
                {"metricName":"temperature","value":80.0,"timestamp":"2026-07-03T10:00:00"}
                """;
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestMetric_missingTimestamp_returns400() throws Exception {
        var body = """
                {"deviceId":1,"metricName":"temperature","value":80.0}
                """;
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestMetric_unknownDevice_returns404() throws Exception {
        var request = new CreateMetricRequest(99999L, "temperature", 80.0, FIXED_TS);
        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
