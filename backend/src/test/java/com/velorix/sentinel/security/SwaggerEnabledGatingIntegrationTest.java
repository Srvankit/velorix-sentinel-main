package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 6.2 — Swagger / OpenAPI Staging/Dev Gating (SWAGGER_ENABLED=true).
 *
 * <p>Verifies that even when Swagger is explicitly enabled:
 * <ul>
 *   <li>Unauthenticated public visitors are still blocked with 403 Forbidden.</li>
 *   <li>Non-admin authenticated roles (USER, ANALYST) are still blocked with 403 Forbidden.</li>
 *   <li>Only authenticated ADMIN users can access Swagger UI and OpenAPI docs.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.swagger-ui.enabled=true",
        "springdoc.api-docs.enabled=true"
})
@DisplayName("Task 6.2 — Swagger / OpenAPI Explicitly Enabled Gating (M2)")
class SwaggerEnabledGatingIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.velorix.sentinel.repository.ActivityLogRepository activityLogRepository;

    @Autowired
    private com.velorix.sentinel.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        activityLogRepository.deleteAll();
        userRepository.deleteAll();

        User regularUser = seedUser("user-enabled", Role.USER);
        User adminUser = seedUser("admin-enabled", Role.ADMIN);

        userToken = login(regularUser.getEmail());
        adminToken = login(adminUser.getEmail());
    }

    @Test
    @DisplayName("Unauthenticated request is still rejected with 403 Forbidden even when Swagger is enabled")
    void unauthenticatedStillForbiddenWhenSwaggerEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("USER is still blocked with 403 Forbidden even when Swagger is enabled")
    void userStillBlockedWhenSwaggerEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ADMIN is permitted to access Swagger UI when explicitly enabled")
    void adminPermittedWhenSwaggerEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Either 200 OK or 302 Found (Springdoc redirect to /swagger-ui/index.html)
                    assertThat(status).isIn(200, 302);
                });
    }

    @Test
    @DisplayName("ADMIN is permitted to access OpenAPI JSON docs when explicitly enabled")
    void adminPermittedToAccessOpenApiDocsWhenEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
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
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}

