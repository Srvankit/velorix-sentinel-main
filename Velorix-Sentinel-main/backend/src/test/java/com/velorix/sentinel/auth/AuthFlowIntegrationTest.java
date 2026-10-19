package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.service.VerificationTokenService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End to end coverage of the authentication module against a real Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Test
    void registersLogsInRefreshesAndLogsOut() throws Exception {
        String email = "ada.%s@velorix.io".formatted(System.nanoTime());

        // Register
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "email", email,
                        "password", PASSWORD,
                        "confirmPassword", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value(Role.USER.name()))
                .andExpect(jsonPath("$.data.verified").value(false));

        User stored = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");

        // Duplicate email is rejected
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "email", email,
                        "password", PASSWORD,
                        "confirmPassword", PASSWORD)))
                .andExpect(status().isConflict());

        // Wrong password
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", "Wr0ng!Passw0rd")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Login
        JsonNode session = body(mockMvc.perform(
                        json(post("/api/auth/login"), Map.of("email", email, "password", PASSWORD, "rememberMe", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString());

        String accessToken = session.get("accessToken").asText();
        String refreshToken = session.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        // Protected endpoint requires the bearer token
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.fullName").value("Ada Lovelace"));

        // Refresh rotates the token pair
        JsonNode rotated = body(mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String newRefresh = rotated.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // The consumed refresh token is dead
        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());

        // Logout revokes the current refresh token
        mockMvc.perform(json(post("/api/auth/logout"), Map.of("refreshToken", newRefresh)))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWeakPasswordsAndMismatchedConfirmation() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "firstName", "Weak",
                        "lastName", "Password",
                        "email", "weak@velorix.io",
                        "password", "password",
                        "confirmPassword", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "firstName", "Mismatch",
                        "lastName", "Password",
                        "email", "mismatch@velorix.io",
                        "password", PASSWORD,
                        "confirmPassword", PASSWORD + "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("confirmPassword"));
    }

    @Test
    void verifiesEmailAndResetsPassword() throws Exception {
        String email = "grace.%s@velorix.io".formatted(System.nanoTime());
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "firstName", "Grace",
                        "lastName", "Hopper",
                        "email", email,
                        "password", PASSWORD,
                        "confirmPassword", PASSWORD)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        String verificationToken = verificationTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        mockMvc.perform(json(post("/api/auth/verify-email"), Map.of("token", verificationToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));

        // Single use
        mockMvc.perform(json(post("/api/auth/verify-email"), Map.of("token", verificationToken)))
                .andExpect(status().isUnauthorized());

        // Forgot password never discloses account existence
        mockMvc.perform(json(post("/api/auth/forgot-password"), Map.of("email", "nobody@velorix.io")))
                .andExpect(status().isOk());

        String newPassword = "Even!Str0nger1";
        String resetToken = verificationTokenService.issue(user, TokenPurpose.PASSWORD_RESET);
        mockMvc.perform(json(post("/api/auth/reset-password"),
                        Map.of("token", resetToken, "newPassword", newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", newPassword)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Map<String, ?> payload) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private JsonNode body(String response) throws Exception {
        return objectMapper.readTree(response).get("data");
    }
}
