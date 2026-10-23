package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import com.velorix.sentinel.entity.Packet;
import com.velorix.sentinel.entity.Threat;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
 * Authorization regression tests verifying tenant isolation, cross-user denial,
 * non-disclosing responses (404), verified principal binding, and administrator access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationOwnershipIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

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
    private AnalysisRepository analysisRepository;

    @Autowired
    private PacketRepository packetRepository;

    @Autowired
    private ThreatRepository threatRepository;

    @Autowired
    private AnalysisSummaryRepository analysisSummaryRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.velorix.sentinel.repository.VerificationTokenRepository verificationTokenRepository;

    private User userA;
    private User userB;
    private User admin;

    private String tokenA;
    private String tokenB;
    private String tokenAdmin;

    private UploadedFile fileA;
    private Analysis analysisA;
    private Packet packetA;
    private Threat threatA;

    @BeforeEach
    void setUp() throws Exception {
        threatRepository.deleteAll();
        analysisSummaryRepository.deleteAll();
        packetRepository.deleteAll();
        analysisRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        activityLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        userA = seedUser("alice", Role.USER);
        userB = seedUser("mallory", Role.USER);
        admin = seedUser("admin", Role.ADMIN);

        tokenA = login(userA.getEmail());
        tokenB = login(userB.getEmail());
        tokenAdmin = login(admin.getEmail());

        fileA = uploadedFileRepository.save(UploadedFile.builder()
                .user(userA)
                .originalFileName("alice-capture.pcap")
                .storedFileName("alice-%s.pcap".formatted(System.nanoTime()))
                .fileSize(2048L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("a".repeat(64))
                .build());

        analysisA = analysisRepository.save(Analysis.builder()
                .uploadedFile(fileA)
                .status(AnalysisStatus.COMPLETED)
                .startedAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now().minusSeconds(10))
                .duration(50_000L)
                .totalPackets(1L)
                .maliciousPackets(1L)
                .safePackets(0L)
                .riskScore(85)
                .analysisVersion("1.0.0")
                .build());

        packetA = packetRepository.save(Packet.builder()
                .analysis(analysisA)
                .packetNumber(1L)
                .timestamp(Instant.now())
                .sourceIp("192.168.1.100")
                .destinationIp("10.0.0.1")
                .sourcePort(54321)
                .destinationPort(80)
                .protocol(Protocol.TCP)
                .packetLength(64)
                .tcpFlags("SYN")
                .payloadSize(0)
                .suspicious(true)
                .build());

        threatA = threatRepository.save(Threat.builder()
                .analysis(analysisA)
                .threatType(ThreatType.PORT_SCAN)
                .severity(ThreatSeverity.HIGH)
                .confidenceScore(0.88d)
                .title("Reconnaissance port scan")
                .description("Port scan detected against subnet")
                .recommendation("Review firewall rules")
                .detectedAt(Instant.now())
                .build());

        analysisSummaryRepository.save(AnalysisSummary.builder()
                .analysis(analysisA)
                .totalThreats(1L)
                .highThreats(1L)
                .riskScore(85)
                .overallStatus(OverallStatus.HIGH_RISK)
                .build());
    }

    @Test
    @DisplayName("User A can access their own uploaded file, analysis, packets, threats, and trigger detection")
    void ownerCanAccessOwnResources() throws Exception {
        String auth = "Bearer " + tokenA;

        // File details
        mockMvc.perform(get("/api/v1/files/" + fileA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fileA.getId().toString()))
                .andExpect(jsonPath("$.data.originalFileName").value("alice-capture.pcap"));

        // Analysis details
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.id").value(analysisA.getId().toString()))
                .andExpect(jsonPath("$.data.analysis.totalPackets").value(1));

        // Analysis summary
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/summary").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskScore").value(85));

        // Packets list
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/packets").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sourceIp").value("192.168.1.100"));

        // Single packet detail
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/packets/" + packetA.getId())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(packetA.getId().toString()));

        // Threat detail
        mockMvc.perform(get("/api/v1/threats/" + threatA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(threatA.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("Reconnaissance port scan"));

        // Detection re-run on completed analysis
        mockMvc.perform(post("/api/v1/threats/run/" + analysisA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("User B is denied access (HTTP 404) when attempting to read User A's resources")
    void crossUserReadAccessDeniedReturning404() throws Exception {
        String auth = "Bearer " + tokenB;

        // Cannot read User A's capture
        mockMvc.perform(get("/api/v1/files/" + fileA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Cannot read User A's analysis
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Cannot read User A's analysis summary
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/summary").header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Cannot list User A's packets
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/packets").header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Cannot read single packet of User A
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/packets/" + packetA.getId())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Cannot read User A's threat
        mockMvc.perform(get("/api/v1/threats/" + threatA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B is denied modification or execution (HTTP 404) on User A's resources")
    void crossUserModificationAccessDeniedReturning404() throws Exception {
        String auth = "Bearer " + tokenB;

        // User B cannot delete User A's file
        mockMvc.perform(delete("/api/v1/files/" + fileA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());

        // Verify the file was not deleted
        UploadedFile notDeleted = uploadedFileRepository.findById(fileA.getId()).orElseThrow();
        assertThat(notDeleted.getUploadStatus()).isEqualTo(UploadStatus.READY_FOR_ANALYSIS);

        // User B cannot start analysis on User A's file
        mockMvc.perform(post("/api/v1/analysis/start/" + fileA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());

        // User B cannot trigger detection on User A's analysis
        mockMvc.perform(post("/api/v1/threats/run/" + analysisA.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Feed and list endpoints strictly isolate records by owner")
    void feedEndpointsIsolateUserRecords() throws Exception {
        String authB = "Bearer " + tokenB;
        String authA = "Bearer " + tokenA;

        // User B sees zero items across all list endpoints
        mockMvc.perform(get("/api/v1/files").header("Authorization", authB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/analysis").header("Authorization", authB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/threats").header("Authorization", authB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // User A sees their own items
        mockMvc.perform(get("/api/v1/files").header("Authorization", authA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/analysis").header("Authorization", authA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/threats").header("Authorization", authA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Administrator can access cross-user resources as designed")
    void adminCanAccessCrossUserResources() throws Exception {
        String auth = "Bearer " + tokenAdmin;

        // Admin can inspect User A's file
        mockMvc.perform(get("/api/v1/files/" + fileA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fileA.getId().toString()));

        // Admin can inspect User A's analysis
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.id").value(analysisA.getId().toString()));

        // Admin can inspect User A's packets
        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId() + "/packets").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Admin can inspect User A's threat
        mockMvc.perform(get("/api/v1/threats/" + threatA.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(threatA.getId().toString()));

        // Admin sees all analyses across users in list view
        mockMvc.perform(get("/api/v1/analysis").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Caller identity is bound strictly to verified principal, ignoring spoofed client user IDs")
    void callerIdentityDerivedStrictlyFromVerifiedPrincipal() throws Exception {
        String authB = "Bearer " + tokenB;
        String spoofedParam = userA.getId().toString();

        // User B passing userA's ID in query parameters cannot bypass ownership
        mockMvc.perform(get("/api/v1/files/" + fileA.getId())
                        .param("userId", spoofedParam)
                        .param("ownerId", spoofedParam)
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId())
                        .param("userId", spoofedParam)
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/analysis/start/" + fileA.getId())
                        .param("userId", spoofedParam)
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/threats/run/" + analysisA.getId())
                        .param("userId", spoofedParam)
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/files")
                        .param("userId", spoofedParam)
                        .header("Authorization", authB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("Unauthenticated requests to secured endpoints are rejected with HTTP 401")
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/" + fileA.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/analysis/" + analysisA.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/threats/" + threatA.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/files/" + fileA.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/analysis/start/" + fileA.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Owner can delete own capture, and subsequent access returns HTTP 404")
    void ownerCanDeleteOwnCapture() throws Exception {
        UploadedFile deletable = uploadedFileRepository.save(UploadedFile.builder()
                .user(userA)
                .originalFileName("deletable.pcap")
                .storedFileName("deletable-%s.pcap".formatted(System.nanoTime()))
                .fileSize(1024L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("b".repeat(64))
                .build());

        // User B cannot delete User A's file
        mockMvc.perform(delete("/api/v1/files/" + deletable.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User A deletes own file
        mockMvc.perform(delete("/api/v1/files/" + deletable.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // File is now marked as DELETED and returns 404
        mockMvc.perform(get("/api/v1/files/" + deletable.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        UploadedFile record = uploadedFileRepository.findById(deletable.getId()).orElseThrow();
        assertThat(record.getUploadStatus()).isEqualTo(UploadStatus.DELETED);
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
