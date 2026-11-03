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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 6.2 — Swagger / OpenAPI Production Gating (M2).
 *
 * <p>Verifies default and production behavior:
 * <ul>
 *   <li>Swagger UI and OpenAPI endpoints are not exposed to unauthenticated visitors.</li>
 *   <li>Unauthenticated requests return 403 Forbidden (or 404 Not Found).</li>
 *   <li>Non-admin authenticated roles (USER, ANALYST) receive 403 Forbidden.</li>
 *   <li>In disabled state, ADMIN receives 404 Not Found or 403 Forbidden.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 6.2 — Swagger / OpenAPI Production Gating (M2)")
class SwaggerProductionGatingIntegrationTest {

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
    private String analystToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        activityLogRepository.deleteAll();
        userRepository.deleteAll();

        User regularUser = seedUser("user", Role.USER);
        User analystUser = seedUser("analyst", Role.ANALYST);
        User adminUser = seedUser("admin", Role.ADMIN);

        userToken = login(regularUser.getEmail());
        analystToken = login(analystUser.getEmail());
        adminToken = login(adminUser.getEmail());
    }

    @Test
    @DisplayName("Unauthenticated GET /swagger-ui.html returns 403 Forbidden or 404 Not Found")
    void unauthenticatedGetSwaggerUiReturnsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    @DisplayName("Unauthenticated GET /swagger-ui/index.html returns 403 Forbidden or 404 Not Found")
    void unauthenticatedGetSwaggerIndexReturnsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    @DisplayName("Unauthenticated GET /v3/api-docs returns 403 Forbidden or 404 Not Found")
    void unauthenticatedGetOpenApiDocsReturnsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    @DisplayName("Unauthenticated GET /v3/api-docs/swagger-config returns 403 Forbidden or 404 Not Found")
    void unauthenticatedGetOpenApiDocsSubpathReturnsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    @DisplayName("Unauthenticated Swagger attempt returns standard API error envelope when 403")
    void unauthenticatedSwaggerReturnsApiErrorEnvelope() throws Exception {
        var mvcResult = mockMvc.perform(get("/swagger-ui.html")).andReturn();
        if (mvcResult.getResponse().getStatus() == 403) {
            assertThat(mvcResult.getResponse().getContentAsString()).contains("FORBIDDEN");
        }
    }

    @Test
    @DisplayName("Authenticated USER is blocked with 403 Forbidden from /swagger-ui.html")
    void regularUserBlockedFromSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated USER is blocked with 403 Forbidden from /v3/api-docs")
    void regularUserBlockedFromOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated ANALYST is blocked with 403 Forbidden from /swagger-ui.html")
    void analystBlockedFromSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated ADMIN in default/production disabled state receives 404 Not Found or 403")
    void adminInDisabledStateReturnsNotFoundOrForbidden() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
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
