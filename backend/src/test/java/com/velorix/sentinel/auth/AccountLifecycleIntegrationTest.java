package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.dto.auth.ChangePasswordRequest;
import com.velorix.sentinel.dto.auth.DeleteAccountRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.OAuthExchangeCodeRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.storage.FileStorageService;
import java.nio.ByteBuffer;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end integration coverage for Task 4.4:
 * - Authenticated change-password endpoint (credential checks, complexity, session revocation)
 * - Self-service account deletion (password/confirmation verification, full DB cascading, S3/storage binary cleanup)
 * - Cross-user data isolation and post-deletion access prevention
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLifecycleIntegrationTest {

    private static final String INITIAL_PASSWORD = "Init!Passw0rd123";
    private static final String NEW_PASSWORD = "Brand!NewPassw0rd456";
    private static final int PCAP_MAGIC = 0xa1b2c3d4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private OAuthExchangeCodeRepository oauthExchangeCodeRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    @AfterEach
    void cleanDatabaseState() {
        for (var file : uploadedFileRepository.findAll()) {
            fileStorageService.delete(file.getStoredFileName());
        }
        uploadedFileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        oauthExchangeCodeRepository.deleteAll();
        activityLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Change Password Flow")
    class ChangePasswordTests {

        @Test
        @DisplayName("Authenticated user can change password; old password fails, new password authenticates, and previous sessions are revoked")
        void authenticatedUserCanChangePassword() throws Exception {
            User user = seedUser("pwd-change", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String accessToken = session.get("accessToken");
            String refreshToken = session.get("refreshToken");

            // Change password
            ChangePasswordRequest changeReq = new ChangePasswordRequest(INITIAL_PASSWORD, NEW_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"));

            // 1. Old password no longer authenticates
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), INITIAL_PASSWORD, false))))
                    .andExpect(status().isUnauthorized());

            // 2. New password authenticates successfully
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), NEW_PASSWORD, false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 3. Old refresh token is revoked
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Incorrect current password is rejected with 400 Bad Request")
        void incorrectCurrentPasswordRejected() throws Exception {
            User user = seedUser("wrong-pwd", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String accessToken = session.get("accessToken");

            ChangePasswordRequest changeReq = new ChangePasswordRequest("Wrong!Password999", NEW_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));

            // Initial password still authenticates
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), INITIAL_PASSWORD, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("New password matching current password is rejected with 400 Bad Request")
        void sameNewPasswordRejected() throws Exception {
            User user = seedUser("same-pwd", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String accessToken = session.get("accessToken");

            ChangePasswordRequest changeReq = new ChangePasswordRequest(INITIAL_PASSWORD, INITIAL_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("New password must be different from current password"));
        }

        @Test
        @DisplayName("New password failing complexity policy is rejected with 400 Bad Request")
        void weakNewPasswordRejected() throws Exception {
            User user = seedUser("weak-pwd", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String accessToken = session.get("accessToken");

            ChangePasswordRequest changeReq = new ChangePasswordRequest(INITIAL_PASSWORD, "simple");
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Unauthenticated request to change password returns 401 Unauthorized")
        void unauthenticatedChangePasswordRejected() throws Exception {
            ChangePasswordRequest changeReq = new ChangePasswordRequest(INITIAL_PASSWORD, NEW_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeReq)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Account Deletion Flow")
    class AccountDeletionTests {

        @Test
        @DisplayName("Authenticated user can delete account: database rows cascade-deleted and storage binaries purged")
        void authenticatedUserCanDeleteAccount() throws Exception {
            User user = seedUser("delete-target", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String token = session.get("accessToken");
            UUID userId = user.getId();

            // Upload a capture to verify storage cleanup
            String uploadBody = mockMvc.perform(multipart("/api/v1/files/upload")
                            .file(capture("sample.pcap", 256, 1))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String storedFileName = objectMapper.readTree(uploadBody).path("data").path("storedFileName").asText();
            assertThat(fileStorageService.exists(storedFileName)).isTrue();
            assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).isEqualTo(1L);

            // Delete account
            DeleteAccountRequest delReq = new DeleteAccountRequest(INITIAL_PASSWORD, null);
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Account deleted successfully"));

            // 1. User record deleted from database
            assertThat(userRepository.findById(userId)).isEmpty();

            // 2. Uploaded files metadata deleted
            assertThat(uploadedFileRepository.findAllByUserId(userId)).isEmpty();

            // 3. Storage binary purged from file store
            assertThat(fileStorageService.exists(storedFileName)).isFalse();

            // 4. Refresh tokens deleted
            assertThat(refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId)).isEmpty();

            // 5. User cannot login again
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), INITIAL_PASSWORD, false))))
                    .andExpect(status().isUnauthorized());

            // 6. Access token can no longer access protected endpoints
            mockMvc.perform(get("/api/v1/files")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());

            // 7. Repeated deletion attempt fails with 401
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Incorrect password for account deletion is rejected with 400 Bad Request; account remains intact")
        void incorrectPasswordForDeletionRejected() throws Exception {
            User user = seedUser("wrong-del-pwd", INITIAL_PASSWORD, false);
            Map<String, String> session = login(user.getEmail(), INITIAL_PASSWORD);
            String token = session.get("accessToken");

            DeleteAccountRequest delReq = new DeleteAccountRequest("WrongPassword!", null);
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Incorrect password for account deletion"));

            // Account is still intact and can authenticate
            assertThat(userRepository.findById(user.getId())).isPresent();
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), INITIAL_PASSWORD, false))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unauthenticated deletion request is rejected with 401 Unauthorized")
        void unauthenticatedDeletionRejected() throws Exception {
            DeleteAccountRequest delReq = new DeleteAccountRequest(INITIAL_PASSWORD, null);
            mockMvc.perform(delete("/api/v1/auth/account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/auth/account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("OAuth user can delete account using 'DELETE' confirmation phrase")
        void oauthUserCanDeleteWithConfirmation() throws Exception {
            User oauthUser = seedUser("oauth-del", INITIAL_PASSWORD, true);
            String token = jwtTokenProvider.issueAccessToken(
                    oauthUser.getId(), oauthUser.getEmail(), oauthUser.getRole().authority()).token();

            DeleteAccountRequest delReq = new DeleteAccountRequest(null, "DELETE");
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(userRepository.findById(oauthUser.getId())).isEmpty();
        }

        @Test
        @DisplayName("OAuth user without confirmation is rejected with 400 Bad Request")
        void oauthUserWithoutConfirmationRejected() throws Exception {
            User oauthUser = seedUser("oauth-no-conf", INITIAL_PASSWORD, true);
            String token = jwtTokenProvider.issueAccessToken(
                    oauthUser.getId(), oauthUser.getEmail(), oauthUser.getRole().authority()).token();

            DeleteAccountRequest delReq = new DeleteAccountRequest(null, "wrong-phrase");
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Please confirm account deletion by typing 'DELETE'"));

            assertThat(userRepository.findById(oauthUser.getId())).isPresent();
        }

        @Test
        @DisplayName("Deleting User A does NOT affect User B's data or storage objects")
        void deletionDoesNotAffectOtherUsersData() throws Exception {
            User userA = seedUser("user-a", INITIAL_PASSWORD, false);
            User userB = seedUser("user-b", INITIAL_PASSWORD, false);

            Map<String, String> sessionA = login(userA.getEmail(), INITIAL_PASSWORD);
            Map<String, String> sessionB = login(userB.getEmail(), INITIAL_PASSWORD);

            // Both upload a capture
            String bodyA = mockMvc.perform(multipart("/api/v1/files/upload")
                            .file(capture("capture-a.pcap", 256, 1))
                            .header("Authorization", "Bearer " + sessionA.get("accessToken")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String storedNameA = objectMapper.readTree(bodyA).path("data").path("storedFileName").asText();

            String bodyB = mockMvc.perform(multipart("/api/v1/files/upload")
                            .file(capture("capture-b.pcap", 256, 2))
                            .header("Authorization", "Bearer " + sessionB.get("accessToken")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String storedNameB = objectMapper.readTree(bodyB).path("data").path("storedFileName").asText();

            assertThat(fileStorageService.exists(storedNameA)).isTrue();
            assertThat(fileStorageService.exists(storedNameB)).isTrue();

            // User A deletes account
            DeleteAccountRequest delReq = new DeleteAccountRequest(INITIAL_PASSWORD, null);
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + sessionA.get("accessToken"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(delReq)))
                    .andExpect(status().isOk());

            // User A data is gone
            assertThat(userRepository.findById(userA.getId())).isEmpty();
            assertThat(fileStorageService.exists(storedNameA)).isFalse();

            // User B data remains 100% intact
            assertThat(userRepository.findById(userB.getId())).isPresent();
            assertThat(fileStorageService.exists(storedNameB)).isTrue();
            assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(userB.getId(), UploadStatus.DELETED))
                    .isEqualTo(1L);

            // User B can still access their captures
            mockMvc.perform(get("/api/v1/files")
                            .header("Authorization", "Bearer " + sessionB.get("accessToken")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    private User seedUser(String prefix, String password, boolean isOAuth) {
        User.UserBuilder<?, ?> builder = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("%s.%s@velorix.io".formatted(prefix, System.nanoTime()))
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .verified(true)
                .active(true);

        if (isOAuth) {
            builder.oauthProvider("github")
                   .oauthProviderId("gh-" + System.nanoTime());
        }

        return userRepository.save(builder.build());
    }

    private Map<String, String> login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password, false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        return Map.of(
                "accessToken", data.path("accessToken").asText(),
                "refreshToken", data.path("refreshToken").asText());
    }

    private MockMultipartFile capture(String name, int size, int seed) {
        byte[] bytes = new byte[size];
        ByteBuffer.wrap(bytes).putInt(PCAP_MAGIC);
        for (int i = 4; i < size; i++) {
            bytes[i] = (byte) ((i + seed * 31) % 251);
        }
        return new MockMultipartFile("file", name, "application/vnd.tcpdump.pcap", bytes);
    }
}

