package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.dto.auth.OAuthExchangeRequest;
import com.velorix.sentinel.entity.OAuthExchangeCode;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.OAuthExchangeCodeRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.service.AuthService;
import com.velorix.sentinel.util.TokenUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthCodeExchangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthExchangeCodeRepository oauthExchangeCodeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User activeUser;

    @BeforeEach
    void setUp() {
        cleanUp();
        activeUser = userRepository.save(User.builder()
                .firstName("Grace")
                .lastName("Hopper")
                .email("grace@velorix.io")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .oauthProvider("google")
                .oauthProviderId("google-1001")
                .build());
    }

    @AfterEach
    void cleanUp() {
        activityLogRepository.deleteAll();
        oauthExchangeCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Valid code exchange succeeds at /api/auth/oauth/exchange and returns full session")
    void validCodeExchangeSucceedsAtApiAuthEndpoint() throws Exception {
        String code = authService.createOAuthExchangeCode(activeUser);

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("grace@velorix.io"));

        // Verify the code is marked consumed in database
        String codeHash = TokenUtils.sha256Hex(code);
        OAuthExchangeCode persisted = oauthExchangeCodeRepository.findByCodeHash(codeHash).orElseThrow();
        assertThat(persisted.isConsumed()).isTrue();
        assertThat(persisted.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("Valid code exchange also succeeds at /api/v1/auth/oauth/exchange")
    void validCodeExchangeSucceedsAtApiV1Endpoint() throws Exception {
        String code = authService.createOAuthExchangeCode(activeUser);

        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("grace@velorix.io"));
    }

    @Test
    @DisplayName("Protected /api/v1/auth/me rejects unauthenticated access with 401")
    void unauthenticatedApiV1AuthMeIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected /api/v1/auth/link-oauth rejects unauthenticated access with 401")
    void unauthenticatedApiV1AuthLinkOAuthIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/link-oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"google\",\"providerId\":\"sub-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Replaying an already consumed exchange code returns 401 Unauthorized")
    void replayingExchangeCodeReturns401() throws Exception {
        String code = authService.createOAuthExchangeCode(activeUser);

        // First redemption succeeds
        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                .andExpect(status().isOk());

        // Replay attempt fails with 401
        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Expired exchange code (>60s) returns 401 Unauthorized")
    void expiredExchangeCodeReturns401() throws Exception {
        String rawCode = TokenUtils.randomToken();
        String codeHash = TokenUtils.sha256Hex(rawCode);

        // Save a code expired 5 seconds ago
        oauthExchangeCodeRepository.save(OAuthExchangeCode.builder()
                .codeHash(codeHash)
                .user(activeUser)
                .expiresAt(Instant.now().minusSeconds(5))
                .build());

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(rawCode))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unknown or corrupted code returns 401 Unauthorized")
    void unknownCodeReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest("nonexistent-oauth-code-value"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Blank code returns 400 Bad Request")
    void blankCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Exchanging code for deactivated account returns 401 Unauthorized")
    void deactivatedUserCodeExchangeReturns401() throws Exception {
        String code = authService.createOAuthExchangeCode(activeUser);

        // Deactivate user
        activeUser.setActive(false);
        userRepository.save(activeUser);

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Concurrent exchange requests for the same code allow exactly one redemption (anti-replay under lock)")
    void concurrentExchangeAllowsExactlyOneSuccess() throws Exception {
        String code = authService.createOAuthExchangeCode(activeUser);

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger unauthorizedCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            tasks.add(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/auth/oauth/exchange")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new OAuthExchangeRequest(code))))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 401) {
                        unauthorizedCount.incrementAndGet();
                    } else {
                        otherCount.incrementAndGet();
                    }
                } finally {
                    finishLatch.countDown();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(unauthorizedCount.get()).isEqualTo(concurrency - 1);
        assertThat(otherCount.get()).isEqualTo(0);
    }
}
