package com.velorix.sentinel.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 6.5 — Comprehensive Actuator Health Probes & Group Separation Verification.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Public /actuator/health returns 200 UP without leaking internal component details.</li>
 *   <li>Liveness group (/actuator/health/liveness) returns 200 UP.</li>
 *   <li>Readiness group (/actuator/health/readiness) returns 200 UP.</li>
 *   <li>Authenticated ADMIN access displays comprehensive component health including
 *       storage and inspectionQueue.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 6.5 — Comprehensive Actuator Health Probes Integration Test")
class ActuatorComprehensiveHealthIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = userRepository.save(User.builder()
                .firstName("Admin")
                .lastName("Operator")
                .email("admin.health.%s@velorix.io".formatted(System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.ADMIN)
                .verified(true)
                .active(true)
                .build());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", adminUser.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        adminToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Public unauthenticated /actuator/health returns 200 UP without internal component leakage")
    void publicHealthReturnsUpWithoutComponentLeakage() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.has("components"))
                .as("Public health probe must not expose internal components to anonymous visitors")
                .isFalse();
    }

    @Test
    @DisplayName("Public /actuator/health/liveness returns 200 UP for process liveness")
    void publicLivenessGroupReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Public /actuator/health/readiness returns 200 UP for workload readiness")
    void publicReadinessGroupReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Authenticated ADMIN /actuator/health displays full component details including storage and inspectionQueue")
    void adminHealthDisplaysComponents() throws Exception {
        String body = mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.has("components")).isTrue();

        JsonNode components = root.get("components");
        assertThat(components.has("storage"))
                .as("Comprehensive health must contain custom storage health component")
                .isTrue();

        assertThat(components.has("inspectionQueue"))
                .as("Comprehensive health must contain custom inspectionQueue health component")
                .isTrue();

        JsonNode storage = components.get("storage");
        assertThat(storage.get("status").asText()).isEqualTo("UP");

        JsonNode queue = components.get("inspectionQueue");
        assertThat(queue.get("status").asText()).isEqualTo("UP");
    }
}
