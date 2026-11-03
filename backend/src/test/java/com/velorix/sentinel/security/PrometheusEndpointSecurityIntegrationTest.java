package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task 6.5 — Prometheus Metrics Endpoint Security & Telemetry Verification.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Prometheus metrics endpoint (/actuator/prometheus) is strictly secured.</li>
 *   <li>Unauthenticated requests return 401 Unauthorized or 403 Forbidden.</li>
 *   <li>Regular authenticated users (ROLE_USER) receive 403 Forbidden.</li>
 *   <li>Administrators (ROLE_ADMIN) can successfully scrape /actuator/prometheus.</li>
 *   <li>Metrics include JVM runtime, HTTP request timers, and custom DPI executor gauges.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 6.5 — Prometheus Endpoint Security & Telemetry Integration Test")
class PrometheusEndpointSecurityIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User regularUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        regularUser = seedUser("user", Role.USER);
        adminUser = seedUser("admin", Role.ADMIN);

        userToken = login(regularUser.getEmail());
        adminToken = login(adminUser.getEmail());
    }

    @Test
    @DisplayName("Unauthenticated GET /actuator/prometheus is rejected with 401 or 403")
    void unauthenticatedAccessToPrometheusIsRejected() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(401, 403);
                });
    }

    @Test
    @DisplayName("Authenticated ROLE_USER GET /actuator/prometheus receives 403 Forbidden")
    void regularUserAccessToPrometheusIsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated ROLE_ADMIN GET /actuator/prometheus succeeds and returns Prometheus format with metrics")
    void adminAccessToPrometheusReturnsMetrics() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String contentType = result.getResponse().getContentType();

        assertThat(contentType)
                .as("Prometheus endpoint must return text/plain or Prometheus format")
                .contains("text/plain");

        // Verify JVM metrics
        assertThat(body)
                .as("Prometheus output must include JVM memory or process metrics")
                .contains("jvm_");

        // Verify HTTP request metrics
        assertThat(body)
                .as("Prometheus output must include HTTP server request timer metrics")
                .contains("http_server_requests_seconds");

        // Verify inspection executor metrics (dots converted to underscores in Prometheus)
        assertThat(body)
                .as("Prometheus output must expose live inspection_executor_active gauge")
                .contains("inspection_executor_active");

        assertThat(body)
                .as("Prometheus output must expose live inspection_executor_pool_size gauge")
                .contains("inspection_executor_pool_size");

        assertThat(body)
                .as("Prometheus output must expose live inspection_executor_queue_depth gauge")
                .contains("inspection_executor_queue_depth");

        assertThat(body)
                .as("Prometheus output must expose live inspection_executor_queue_capacity gauge")
                .contains("inspection_executor_queue_capacity");
    }

    private User seedUser(String prefix, Role role) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName(role.name())
                .email("%s.%s@velorix.io".formatted(prefix, System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .verified(true)
                .active(true)
                .build());
    }

    private String login(String email) throws Exception {
        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).path("data").path("accessToken").asText();
    }
}
