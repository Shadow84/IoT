package com.example.demo.controller;

import com.example.demo.dto.CreateDeviceRequest;
import com.example.demo.dto.UpdateDeviceRequest;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeviceControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void registerDevice_validRequest_returns201WithBody() throws Exception {
        var request = new CreateDeviceRequest(
                "Warehouse-A Temp Sensor", DeviceType.SENSOR, "Building 3",
                Map.of("serialNumber", "SN-001"));

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Warehouse-A Temp Sensor"))
                .andExpect(jsonPath("$.type").value("SENSOR"))
                .andExpect(jsonPath("$.location").value("Building 3"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.extraAttributes.serialNumber").value("SN-001"));
    }

    @Test
    void registerDevice_missingName_returns400() throws Exception {
        var body = """
                {"type":"SENSOR","location":"Room 1"}
                """;
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerDevice_missingType_returns400() throws Exception {
        var body = """
                {"name":"Sensor","location":"Room 1"}
                """;
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDevices_withExplicitParams_returnsPagedResponse() throws Exception {
        var request = new CreateDeviceRequest("D1", DeviceType.GATEWAY, "Floor 1", null);
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/devices")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "id")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("D1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listDevices_withDefaultParams_returnsPagedResponse() throws Exception {
        var request = new CreateDeviceRequest("D2", DeviceType.SENSOR, "Floor 2", null);
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("D2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getDevice_existingId_returns200() throws Exception {
        var request = new CreateDeviceRequest("D1", DeviceType.CONTROLLER, "Lab", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/devices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("D1"));
    }

    @Test
    void getDevice_missingId_returns404() throws Exception {
        mockMvc.perform(get("/api/devices/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDevice_patchesName() throws Exception {
        var request = new CreateDeviceRequest("OldName", DeviceType.SENSOR, "Loc", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        var update = new UpdateDeviceRequest("NewName", null, null, null);
        mockMvc.perform(put("/api/devices/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    @Test
    void deactivateDevice_setsStatusInactive() throws Exception {
        var request = new CreateDeviceRequest("ToDeactivate", DeviceType.SENSOR, "Loc", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/devices/" + id + "/deactivate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/devices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void activateDevice_setsStatusActive() throws Exception {
        var request = new CreateDeviceRequest("ToActivate", DeviceType.SENSOR, "Loc", null);
        var result = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/devices/" + id + "/deactivate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/devices/" + id + "/activate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/devices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
