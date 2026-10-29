package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.UnauthorizedException;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.service.AuthService;
import com.velorix.sentinel.service.EmailService;
import com.velorix.sentinel.service.RefreshTokenService;
import com.velorix.sentinel.service.VerificationTokenService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthAccountLinkingIntegrationTest {

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
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @SpyBean
    private EmailService emailService;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        activityLogRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        clearInvocations(emailService);
    }

    @Test
    @DisplayName("a. New account creation with verified provider identity succeeds and creates verified user")
    void newAccountCreationWithVerifiedProviderIdentity() {
        String email = "oauth.new.%s@velorix.io".formatted(System.nanoTime());
        String provider = "google";
        String providerId = "google-sub-" + UUID.randomUUID();

        AuthResponse response = authService.loginWithOAuth(provider, providerId, email, "Ada", "Lovelace", true);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo(email);
        assertThat(response.user().verified()).isTrue();

        User savedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(savedUser.isVerified()).isTrue();
        assertThat(savedUser.getOauthProvider()).isEqualTo(provider);
        assertThat(savedUser.getOauthProviderId()).isEqualTo(providerId);
        assertThat(savedUser.getPassword()).isNotBlank();
        // The generated random password must not match empty string or standard passwords
        assertThat(passwordEncoder.matches("", savedUser.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(PASSWORD, savedUser.getPassword())).isFalse();
    }

    @Test
    @DisplayName("b. Rejection of unverified Google claims (email_verified=false, missing, malformed)")
    void rejectionOfUnverifiedGoogleClaims() {
        String email = "google.unverified.%s@velorix.io".formatted(System.nanoTime());

        // emailVerified = false
        assertThatThrownBy(() -> authService.loginWithOAuth("google", "sub-1", email, "Test", "User", false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Email is not verified by google");

        // Missing email
        assertThatThrownBy(() -> authService.loginWithOAuth("google", "sub-1", "", "Test", "User", true))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("identity provider did not return an email address");

        // Missing provider ID
        assertThatThrownBy(() -> authService.loginWithOAuth("google", "", email, "Test", "User", true))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("OAuth provider subject ID is required");

        // Missing provider
        assertThatThrownBy(() -> authService.loginWithOAuth("", "sub-1", email, "Test", "User", true))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("OAuth provider is required");

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("c. Rejection of unverified GitHub email claim")
    void rejectionOfUnverifiedGitHubEmail() {
        String email = "github.unverified.%s@velorix.io".formatted(System.nanoTime());

        assertThatThrownBy(() -> authService.loginWithOAuth("github", "gh-12345", email, "Octo", "Cat", false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Email is not verified by github");

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("d. Pre-account takeover defense: unverified local account transitions securely to OAuth")
    void unverifiedLocalAccountTransitionToOAuthSecurely() {
        String email = "takeover.victim.%s@velorix.io".formatted(System.nanoTime());
        String oldPassword = "AttackerStagedPassword1!";

        // 1. Create an unverified local account (e.g. registered by attacker who does not own the email)
        User unverifiedUser = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(oldPassword))
                .firstName("Victim")
                .lastName("Local")
                .role(Role.USER)
                .verified(false)
                .active(true)
                .build());

        // Issue active refresh token, verification token, and password-reset token
        RefreshTokenService.IssuedRefreshToken issuedRefresh = refreshTokenService.issue(unverifiedUser, true);
        String verifyToken = verificationTokenService.issue(unverifiedUser, TokenPurpose.EMAIL_VERIFICATION);
        String resetToken = verificationTokenService.issue(unverifiedUser, TokenPurpose.PASSWORD_RESET);

        assertThat(refreshTokenRepository.findByTokenHash(com.velorix.sentinel.util.TokenUtils.sha256Hex(issuedRefresh.token()))).isPresent();
        assertThat(verificationTokenRepository.findByTokenHashAndPurpose(com.velorix.sentinel.util.TokenUtils.sha256Hex(verifyToken), TokenPurpose.EMAIL_VERIFICATION)).isPresent();

        // 2. Legitimate owner logs in via Google OAuth with verified email
        String googleSub = "google-sub-" + UUID.randomUUID();
        AuthResponse authResponse = authService.loginWithOAuth("google", googleSub, email, "Real", "Owner", true);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();

        // 3. Verify user state transitioned securely
        User transitionedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(transitionedUser.isVerified()).isTrue();
        assertThat(transitionedUser.getOauthProvider()).isEqualTo("google");
        assertThat(transitionedUser.getOauthProviderId()).isEqualTo(googleSub);

        // 4. Old refresh tokens must be revoked
        RefreshToken oldStoredRefresh = refreshTokenRepository
                .findByTokenHash(com.velorix.sentinel.util.TokenUtils.sha256Hex(issuedRefresh.token())).orElseThrow();
        assertThat(oldStoredRefresh.isUsable(java.time.Instant.now())).isFalse();

        // 5. Outstanding verification and reset tokens must be consumed
        VerificationToken consumedVerify = verificationTokenRepository
                .findByTokenHashAndPurpose(com.velorix.sentinel.util.TokenUtils.sha256Hex(verifyToken), TokenPurpose.EMAIL_VERIFICATION).orElseThrow();
        assertThat(consumedVerify.isUsable(java.time.Instant.now())).isFalse();

        VerificationToken consumedReset = verificationTokenRepository
                .findByTokenHashAndPurpose(com.velorix.sentinel.util.TokenUtils.sha256Hex(resetToken), TokenPurpose.PASSWORD_RESET).orElseThrow();
        assertThat(consumedReset.isUsable(java.time.Instant.now())).isFalse();

        // 6. Old attacker password must NOT authenticate
        assertThat(passwordEncoder.matches(oldPassword, transitionedUser.getPassword())).isFalse();
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, oldPassword, false)))
                .isInstanceOf(UnauthorizedException.class);

        // 7. Security notification must be sent
        verify(emailService).sendOAuthLinked(eq(transitionedUser), eq("google"));
    }

    @Test
    @DisplayName("e. Already-verified local account is NOT silently linked by matching email (409 Conflict)")
    void alreadyVerifiedLocalAccountNotSilentlyLinked() {
        String email = "verified.local.%s@velorix.io".formatted(System.nanoTime());

        // 1. Existing verified user with password
        User verifiedUser = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("Alice")
                .lastName("Local")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        // 2. Attacker logs in with OAuth claiming same email
        String attackerSub = "google-sub-attacker-" + UUID.randomUUID();
        assertThatThrownBy(() -> authService.loginWithOAuth("google", attackerSub, email, "Attacker", "Social", true))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("An account with this email already exists with password authentication");

        // 3. User remains unaffected: no oauthProvider, password still works, no security email sent
        User unchangedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(unchangedUser.getOauthProvider()).isNull();
        assertThat(unchangedUser.getOauthProviderId()).isNull();
        assertThat(passwordEncoder.matches(PASSWORD, unchangedUser.getPassword())).isTrue();

        // Legitimate user can still login normally
        AuthResponse loginResponse = authService.login(new LoginRequest(email, PASSWORD, false));
        assertThat(loginResponse).isNotNull();
    }

    @Test
    @DisplayName("f. Identity collision: same OAuth identity cannot be linked to multiple accounts")
    void oAuthIdentityCollisionPrevented() {
        String sharedProvider = "github";
        String sharedProviderId = "gh-unique-id-9999";

        // User 1 created with this GitHub identity
        authService.loginWithOAuth(sharedProvider, sharedProviderId, "user1@velorix.io", "User", "One", true);

        // User 2 (unverified) attempts to link the same GitHub identity
        userRepository.save(User.builder()
                .email("user2@velorix.io")
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("User")
                .lastName("Two")
                .role(Role.USER)
                .verified(false)
                .active(true)
                .build());

        assertThatThrownBy(() -> authService.loginWithOAuth(sharedProvider, sharedProviderId, "user2@velorix.io", "User", "Two", true))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("already linked to another user");
    }

    @Test
    @DisplayName("g. Explicit authenticated account-linking flow (/api/auth/link-oauth) links identity and notifies user")
    void explicitAuthenticatedAccountLinkingFlow() throws Exception {
        String email = "link.flow.%s@velorix.io".formatted(System.nanoTime());

        // 1. Create verified user
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("Grace")
                .lastName("Hopper")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        // 2. User logs in to get Bearer access token
        AuthResponse login = authService.login(new LoginRequest(email, PASSWORD, false));
        String accessToken = login.accessToken();

        // 3. Unauthenticated call to link-oauth fails with 401
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/auth/link-oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "provider", "github",
                                "providerId", "gh-explicit-123"))))
                .andExpect(status().isUnauthorized());

        // 4. Authenticated call to link-oauth succeeds with 200
        mockMvc.perform(post("/api/auth/link-oauth")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "provider", "github",
                                "providerId", "gh-explicit-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Social account linked successfully"));

        // Verify database state
        User updatedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(updatedUser.getOauthProvider()).isEqualTo("github");
        assertThat(updatedUser.getOauthProviderId()).isEqualTo("gh-explicit-123");

        // Verify security notification email was dispatched
        verify(emailService).sendOAuthLinked(eq(updatedUser), eq("github"));

        // 5. Subsequent link attempt returns 409 Conflict
        mockMvc.perform(post("/api/auth/link-oauth")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "provider", "google",
                                "providerId", "google-explicit-456"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An OAuth provider is already linked to this account"));
    }

    @Test
    @DisplayName("h. Returning OAuth user can log in repeatedly without collisions or data loss")
    void returningOAuthUserLoginRegression() {
        String email = "returning.oauth.%s@velorix.io".formatted(System.nanoTime());
        String provider = "google";
        String providerId = "google-returning-" + UUID.randomUUID();

        // First login -> creates account
        AuthResponse firstLogin = authService.loginWithOAuth(provider, providerId, email, "First", "Login", true);
        assertThat(firstLogin).isNotNull();

        // Second login with exact same provider and providerId
        AuthResponse secondLogin = authService.loginWithOAuth(provider, providerId, email, "First", "Login", true);
        assertThat(secondLogin).isNotNull();
        assertThat(secondLogin.user().id()).isEqualTo(firstLogin.user().id());
        assertThat(secondLogin.accessToken()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // Provider allowlist validation — linkOAuth()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("i. linkOAuth provider allowlist validation")
    class LinkOAuthProviderValidation {

        private User verifiedUser;
        private String accessToken;

        @BeforeEach
        void setUp() {
            String email = "provider.validation.%s@velorix.io".formatted(System.nanoTime());
            verifiedUser = userRepository.save(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode("Str0ng!Passw0rd"))
                    .firstName("Grace")
                    .lastName("Hopper")
                    .role(Role.USER)
                    .verified(true)
                    .active(true)
                    .build());
            accessToken = authService.login(new com.velorix.sentinel.dto.auth.LoginRequest(email, "Str0ng!Passw0rd", false)).accessToken();
        }

        @Test
        @DisplayName("'google' is accepted as a supported provider")
        void googleProviderIsAccepted() {
            authService.linkOAuth(verifiedUser, "google", "google-sub-validation-" + UUID.randomUUID());

            User updated = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(updated.getOauthProvider()).isEqualTo("google");
        }

        @Test
        @DisplayName("'github' is accepted as a supported provider")
        void githubProviderIsAccepted() {
            authService.linkOAuth(verifiedUser, "github", "gh-validation-" + UUID.randomUUID());

            User updated = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(updated.getOauthProvider()).isEqualTo("github");
        }

        @Test
        @DisplayName("Provider name is normalized to lowercase before validation")
        void providerNameIsNormalizedToLowercase() {
            authService.linkOAuth(verifiedUser, "Google", "google-sub-case-" + UUID.randomUUID());

            User updated = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(updated.getOauthProvider()).isEqualTo("google");
        }

        @Test
        @DisplayName("Unsupported provider is rejected with BadRequestException")
        void unsupportedProviderIsRejected() {
            assertThatThrownBy(() -> authService.linkOAuth(verifiedUser, "facebook", "fb-123"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported OAuth provider 'facebook'");

            User unchanged = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(unchanged.getOauthProvider()).isNull();
        }

        @Test
        @DisplayName("Null provider is rejected with BadRequestException")
        void nullProviderIsRejected() {
            assertThatThrownBy(() -> authService.linkOAuth(verifiedUser, null, "some-id"))
                    .isInstanceOf(BadRequestException.class);

            User unchanged = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(unchanged.getOauthProvider()).isNull();
        }

        @Test
        @DisplayName("Blank provider is rejected with BadRequestException")
        void blankProviderIsRejected() {
            assertThatThrownBy(() -> authService.linkOAuth(verifiedUser, "   ", "some-id"))
                    .isInstanceOf(BadRequestException.class);

            User unchanged = userRepository.findById(verifiedUser.getId()).orElseThrow();
            assertThat(unchanged.getOauthProvider()).isNull();
        }

        @Test
        @DisplayName("Unsupported provider via HTTP endpoint returns 400 Bad Request")
        void unsupportedProviderViaEndpointReturnsBadRequest() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/auth/link-oauth")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"twitter\",\"providerId\":\"tw-123\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported OAuth provider 'twitter'")));
        }

        @Test
        @DisplayName("OAuth identity collision via linkOAuth still rejected with 409 Conflict")
        void oauthIdentityCollisionViaLinkOAuthRejected() throws Exception {
            // Another user already linked this GitHub identity
            String takenEmail = "taken.github.%s@velorix.io".formatted(System.nanoTime());
            String sharedProviderId = "gh-taken-" + UUID.randomUUID();
            authService.loginWithOAuth("github", sharedProviderId, takenEmail, "Other", "User", true);

            // Our user tries to link the same identity
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/auth/link-oauth")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"github\",\"providerId\":\"" + sharedProviderId + "\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already linked to another user")));
        }
    }
}
