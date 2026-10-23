package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.service.VerificationTokenService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationEnforcementIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.velorix.sentinel.repository.ActivityLogRepository activityLogRepository;

    @Autowired
    private com.velorix.sentinel.repository.VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        activityLogRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Registration -> unverified login rejected with 403 (no tokens) -> token consumed -> login succeeds with 200")
    void registrationToVerificationFlow() throws Exception {
        String email = "verify.flow.%s@velorix.io".formatted(System.nanoTime());

        // 1. Register new account -> returns generic 201 with no user data payload
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alan",
                                "lastName", "Turing",
                                "email", email,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If eligible, a verification link has been sent."))
                .andExpect(jsonPath("$.data").doesNotExist());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.isVerified()).isFalse();

        // 2. Attempt login before email verification -> Must be rejected with 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Email verification is required before login."))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 3. Confirm that no tokens (access or refresh) were issued for the unverified user
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())).isEmpty();

        // 4. Verify email using single-use verification token
        String token = verificationTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));

        User verifiedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verifiedUser.isVerified()).isTrue();

        // 4b. Replaying the consumed verification token must be rejected with 401 INVALID_TOKEN
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("This link has expired. Request a new one to continue"));

        // User remains verified and no tokens were issued
        User postReplayUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(postReplayUser.isVerified()).isTrue();
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(verifiedUser.getId())).isEmpty();

        // 5. Login now succeeds and issues token pair
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(loginResponse).path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(verifiedUser.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("Invalid password on unverified user returns 401 Unauthorized, preventing verification state probing")
    void wrongPasswordReturns401EvenIfUnverified() throws Exception {
        String email = "wrong.pass.%s@velorix.io".formatted(System.nanoTime());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Grace",
                                "lastName", "Hopper",
                                "email", email,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // When password is wrong, returns 401 Unauthorized, NOT 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Wrong!Pass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Duplicate registration returns identical 201 envelope structure without exposing account existence")
    void duplicateRegistrationIsHandledGenerically() throws Exception {
        String email = "dup.test.%s@velorix.io".formatted(System.nanoTime());

        // Initial registration (new email)
        String initialResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "First",
                                "lastName", "Registration",
                                "email", email,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If eligible, a verification link has been sent."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long count = userRepository.count();
        assertThat(count).isEqualTo(1);

        // Duplicate registration (existing email)
        String duplicateResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Second",
                                "lastName", "Registration",
                                "email", email,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If eligible, a verification link has been sent."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Database count unchanged - no duplicate user created
        assertThat(userRepository.count()).isEqualTo(count);

        // Assert that the complete public response bodies have identical structure:
        // same keys, same status, same message, no user ID or user fields exposed
        JsonNode tree1 = objectMapper.readTree(initialResponse);
        JsonNode tree2 = objectMapper.readTree(duplicateResponse);

        assertThat(tree1.path("success").asBoolean()).isTrue();
        assertThat(tree2.path("success").asBoolean()).isTrue();
        assertThat(tree1.path("status").asInt()).isEqualTo(201);
        assertThat(tree2.path("status").asInt()).isEqualTo(201);
        assertThat(tree1.path("message").asText()).isEqualTo("If eligible, a verification link has been sent.");
        assertThat(tree2.path("message").asText()).isEqualTo("If eligible, a verification link has been sent.");

        // Neither response exposes user IDs, emails, roles, or internal fields
        assertThat(tree1.has("data")).isFalse();
        assertThat(tree2.has("data")).isFalse();
        assertThat(tree1.has("id")).isFalse();
        assertThat(tree2.has("id")).isFalse();
        assertThat(tree1.has("email")).isFalse();
        assertThat(tree2.has("email")).isFalse();

        // Compare JSON field sets: exactly ["message", "status", "success", "timestamp"]
        List<String> fields1 = new ArrayList<>();
        tree1.fieldNames().forEachRemaining(fields1::add);
        Collections.sort(fields1);

        List<String> fields2 = new ArrayList<>();
        tree2.fieldNames().forEachRemaining(fields2::add);
        Collections.sort(fields2);

        assertThat(fields1).isEqualTo(fields2);
        assertThat(fields1).containsExactly("message", "status", "success", "timestamp");
    }

    @Test
    @DisplayName("Verification token replay attack is rejected with 401 INVALID_TOKEN")
    void tokenReplayAttackIsRejected() throws Exception {
        String email = "replay.standalone.%s@velorix.io".formatted(System.nanoTime());
        User user = userRepository.save(User.builder()
                .firstName("Replay")
                .lastName("Standalone")
                .email(email)
                .password(PASSWORD)
                .role(Role.USER)
                .verified(false)
                .active(true)
                .build());

        String token = verificationTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION);

        // First consumption succeeds
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));

        User verified = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verified.isVerified()).isTrue();

        // Second consumption is rejected with 401 INVALID_TOKEN
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("This link has expired. Request a new one to continue"));

        // User remains verified and no session tokens exist
        User postReplayUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(postReplayUser.isVerified()).isTrue();
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(postReplayUser.getId())).isEmpty();
    }
}
