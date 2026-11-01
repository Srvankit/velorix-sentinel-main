package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.entity.ActivityLog;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.service.RefreshTokenService;
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
class RefreshTokenReuseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User activeUser;

    @BeforeEach
    void setUp() {
        cleanUp();
        activeUser = userRepository.save(User.builder()
                .firstName("Grace")
                .lastName("Hopper")
                .email("grace.hopper." + System.nanoTime() + "@velorix.io")
                .password(passwordEncoder.encode("SecureP@ssw0rd123!"))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        activityLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Normal rotation preserves family_id and records previous_token_hash link")
    void normalRotationPreservesFamilyAndLinksPreviousHash() throws Exception {
        // 1. Initial issue (Token A)
        RefreshTokenService.IssuedRefreshToken issuedA = refreshTokenService.issue(activeUser, true);
        UUID familyId = issuedA.record().getFamilyId();
        assertThat(familyId).isNotNull();
        assertThat(issuedA.record().getPreviousTokenHash()).isNull();

        // 2. Rotate Token A -> Token B
        MvcResult resultB = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issuedA.token()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode jsonB = objectMapper.readTree(resultB.getResponse().getContentAsString());
        String tokenB = jsonB.get("data").get("refreshToken").asText();
        assertThat(tokenB).isNotEqualTo(issuedA.token());

        // Verify Token A in DB: revoked = true
        RefreshToken storedA = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(issuedA.token()))
                .orElseThrow();
        assertThat(storedA.isRevoked()).isTrue();
        assertThat(storedA.getRevokedAt()).isNotNull();

        // Verify Token B in DB: active, same family_id, previous_token_hash points to Token A
        RefreshToken storedB = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokenB))
                .orElseThrow();
        assertThat(storedB.isRevoked()).isFalse();
        assertThat(storedB.getFamilyId()).isEqualTo(familyId);
        assertThat(storedB.getPreviousTokenHash()).isEqualTo(storedA.getTokenHash());

        // 3. Rotate Token B -> Token C
        MvcResult resultC = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tokenB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode jsonC = objectMapper.readTree(resultC.getResponse().getContentAsString());
        String tokenC = jsonC.get("data").get("refreshToken").asText();

        // Verify Token B is now revoked
        RefreshToken storedBAfter = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokenB))
                .orElseThrow();
        assertThat(storedBAfter.isRevoked()).isTrue();

        // Verify Token C in DB: active, same family_id, previous_token_hash points to Token B
        RefreshToken storedC = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokenC))
                .orElseThrow();
        assertThat(storedC.isRevoked()).isFalse();
        assertThat(storedC.getFamilyId()).isEqualTo(familyId);
        assertThat(storedC.getPreviousTokenHash()).isEqualTo(storedBAfter.getTokenHash());

        // Check activity logs
        List<ActivityLog> logs = activityLogRepository.findAll();
        assertThat(logs).anyMatch(l -> l.getActivityType() == ActivityType.TOKEN_REFRESHED && l.isSuccessful());
    }

    @Test
    @DisplayName("Replaying a revoked token triggers reuse detection, revokes the family, and writes security log")
    void replayingRevokedTokenRevokesFamilyAndLogsSecurityEvent() throws Exception {
        // 1. Initial issue (Token A)
        RefreshTokenService.IssuedRefreshToken issuedA = refreshTokenService.issue(activeUser, true);
        UUID familyId = issuedA.record().getFamilyId();

        // 2. Legitimate rotation: Token A -> Token B
        MvcResult resultB = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issuedA.token()))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonB = objectMapper.readTree(resultB.getResponse().getContentAsString());
        String tokenB = jsonB.get("data").get("refreshToken").asText();

        // Confirm Token B is active before replay attack
        RefreshToken storedBBefore = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokenB))
                .orElseThrow();
        assertThat(storedBBefore.isRevoked()).isFalse();

        // 3. Attacker replays revoked Token A
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issuedA.token()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Suspicious token reuse detected; all sessions revoked."));

        // 4. Assert Token B has now been revoked as part of the compromised family
        RefreshToken storedBAfter = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokenB))
                .orElseThrow();
        assertThat(storedBAfter.isRevoked()).isTrue();
        assertThat(storedBAfter.getRevokedAt()).isNotNull();

        // Assert ALL tokens belonging to the family are revoked
        List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyId(familyId);
        assertThat(familyTokens).hasSize(2);
        assertThat(familyTokens).allMatch(RefreshToken::isRevoked);

        // 5. Assert high-severity security event is recorded in activity_logs
        List<ActivityLog> logs = activityLogRepository.findAll();
        assertThat(logs).anyMatch(l ->
                l.getActivityType() == ActivityType.TOKEN_REVOKED
                        && !l.isSuccessful()
                        && l.getDescription() != null
                        && l.getDescription().contains("Suspicious token reuse detected for family " + familyId));

        // 6. Verify subsequent attempt to use Token B also fails with 401 Unauthorized
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tokenB))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Transaction durability: Family revocation and security logs remain committed despite 401 response")
    void transactionDurabilityOnReuseDetection() throws Exception {
        RefreshTokenService.IssuedRefreshToken issuedA = refreshTokenService.issue(activeUser, true);
        UUID familyId = issuedA.record().getFamilyId();

        // Legitimate rotation: A -> B
        MvcResult resultB = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issuedA.token()))))
                .andExpect(status().isOk())
                .andReturn();

        String tokenB = objectMapper.readTree(resultB.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        // Replay Token A
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issuedA.token()))))
                .andExpect(status().isUnauthorized());

        // In a fresh query outside the controller invocation, verify durability in DB
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(activeUser.getId());
        assertThat(activeTokens).isEmpty();

        List<ActivityLog> failedAuditLogs = activityLogRepository.findAll().stream()
                .filter(l -> !l.isSuccessful() && l.getActivityType() == ActivityType.TOKEN_REVOKED)
                .toList();
        assertThat(failedAuditLogs).hasSize(1);
        assertThat(failedAuditLogs.get(0).getDescription()).contains(familyId.toString());
    }

    @Test
    @DisplayName("Concurrent refresh attempts on the same token prevent token chain forking under row lock")
    void concurrentRotationUnderRowLockPreventsForking() throws Exception {
        RefreshTokenService.IssuedRefreshToken issuedA = refreshTokenService.issue(activeUser, true);
        UUID familyId = issuedA.record().getFamilyId();
        String rawTokenA = issuedA.token();

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger unauthorizedCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);
        List<String> issuedTokens = new ArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            tasks.add(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rawTokenA))))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
                        synchronized (issuedTokens) {
                            issuedTokens.add(json.get("data").get("refreshToken").asText());
                        }
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

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(otherCount.get()).as("No unexpected status codes (no 500s or deadlocks)").isZero();

        // Exactly one rotation succeeded or 0 (if race triggered reuse revocation)
        // Under no circumstances can MORE THAN ONE rotation succeed from the same parent token
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
        assertThat(unauthorizedCount.get()).isGreaterThanOrEqualTo(concurrency - 1);

        // Crucial invariant: never allow two distinct valid child tokens from the same parent
        assertThat(issuedTokens.size()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Blank or missing refresh token returns 400 Bad Request")
    void blankRefreshTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Deactivated user cannot rotate refresh token")
    void deactivatedUserCannotRotate() throws Exception {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(activeUser, true);

        // Deactivate account
        activeUser.setActive(false);
        userRepository.save(activeUser);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(issued.token()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This account is deactivated"));
    }

    @Test
    @DisplayName("revokeFamily explicitly revokes all active tokens belonging to that family")
    void revokeFamilyRevokesAllTokensInFamily() {
        RefreshTokenService.IssuedRefreshToken issuedA = refreshTokenService.issue(activeUser, true);
        UUID familyId = issuedA.record().getFamilyId();

        RefreshTokenService.IssuedRefreshToken issuedChild = refreshTokenService.issue(
                activeUser, true, familyId, issuedA.record().getTokenHash());

        assertThat(issuedA.record().isRevoked()).isFalse();
        assertThat(issuedChild.record().isRevoked()).isFalse();

        int count = refreshTokenService.revokeFamily(familyId);
        assertThat(count).isEqualTo(2);

        List<RefreshToken> tokens = refreshTokenRepository.findAllByFamilyId(familyId);
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(RefreshToken::isRevoked);
    }
}
