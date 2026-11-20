package com.velorix.sentinel.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.velorix.sentinel.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 6.4: Container Healthcheck Endpoint Integration Test.
 * Verifies that the actuator health endpoint targeted by the container HEALTHCHECK
 * (curl -f http://localhost:10000/actuator/health) is publicly reachable without authentication
 * and returns 200 OK with UP status.
 */
@DisplayName("Task 6.4 — Container Healthcheck Endpoint Integration Test")
class ActuatorHealthEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health is publicly reachable without auth and returns 200 UP")
    void actuatorHealthReturnsUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/info is publicly reachable without auth and returns 200")
    void actuatorInfoReturns200WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
