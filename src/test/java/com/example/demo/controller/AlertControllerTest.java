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
class AlertControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long deviceId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        var deviceRequest = new CreateDeviceRequest("Sensor A", DeviceType.SENSOR, "Room 1", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceRequest)))
                .andReturn();
        deviceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void listAlerts_emptyInitially_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void getAlertById_existingId_returns200WithBody() throws Exception {
        var ruleRequest = new CreateAlertRuleRequest(
                deviceId, "temperature", AlertOperator.GREATER_THAN, 80.0, AlertSeverity.CRITICAL, true);
        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateMetricRequest(deviceId, "temperature", 95.0, LocalDateTime.of(2026, 7, 3, 10, 0, 0)))))
                .andExpect(status().isNoContent());

        var listResult = mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andReturn();

        var alertId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        mockMvc.perform(get("/api/alerts/" + alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.deviceName").value("Sensor A"))
                .andExpect(jsonPath("$.metricName").value("temperature"));
    }

    @Test
    void getAlertById_missingId_returns404() throws Exception {
        mockMvc.perform(get("/api/alerts/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAlerts_withPaginationParams_respectsPageAndSize() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sortBy", "openedAt")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void listAlerts_invalidPageParams_returns400() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
