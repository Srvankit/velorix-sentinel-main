package com.velorix.sentinel.core;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.Packet;
import com.velorix.sentinel.entity.Threat;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SortValidationIntegrationTest {

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

    private String authToken;
    private Analysis analysis;

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

        User user = userRepository.save(User.builder()
                .firstName("Sort")
                .lastName("Tester")
                .email("sort.tester@velorix.io")
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.ANALYST)
                .verified(true)
                .active(true)
                .build());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(),
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        authToken = "Bearer " + objectMapper.readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(user)
                .originalFileName("test-capture.pcap")
                .storedFileName("test-" + System.nanoTime() + ".pcap")
                .fileSize(1024L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("a".repeat(64))
                .build());

        analysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.COMPLETED)
                .startedAt(Instant.now().minusSeconds(30))
                .completedAt(Instant.now().minusSeconds(10))
                .duration(20_000L)
                .totalPackets(2L)
                .maliciousPackets(1L)
                .safePackets(1L)
                .riskScore(45)
                .analysisVersion("1.0.0")
                .build());

        packetRepository.save(Packet.builder()
                .analysis(analysis)
                .packetNumber(1L)
                .timestamp(Instant.now().minusSeconds(20))
                .sourceIp("192.168.1.10")
                .destinationIp("10.0.0.1")
                .sourcePort(44321)
                .destinationPort(80)
                .protocol(Protocol.TCP)
                .packetLength(128)
                .tcpFlags("SYN")
                .payloadSize(0)
                .suspicious(false)
                .build());

        packetRepository.save(Packet.builder()
                .analysis(analysis)
                .packetNumber(2L)
                .timestamp(Instant.now().minusSeconds(19))
                .sourceIp("192.168.1.10")
                .destinationIp("10.0.0.1")
                .sourcePort(44321)
                .destinationPort(80)
                .protocol(Protocol.TCP)
                .packetLength(256)
                .tcpFlags("ACK")
                .payloadSize(128)
                .suspicious(true)
                .build());

        threatRepository.save(Threat.builder()
                .analysis(analysis)
                .threatType(ThreatType.PORT_SCAN)
                .severity(ThreatSeverity.MEDIUM)
                .confidenceScore(0.85)
                .title("Reconnaissance scan")
                .description("Port scan detected")
                .recommendation("Block source IP")
                .detectedAt(Instant.now().minusSeconds(15))
                .build());
    }

    // --- Analyses Sorting ---

    @Test
    @DisplayName("GET /api/v1/analysis without sort param falls back to default sort (200 OK)")
    void listAnalysesWithDefaultSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/analysis with allowlisted sort succeeds (200 OK)")
    void listAnalysesWithAllowlistedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken)
                        .param("sort", "totalPackets")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/analysis with comma-separated sort expression succeeds (200 OK)")
    void listAnalysesWithCommaSeparatedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken)
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/v1/analysis with non-allowlisted property returns 400 Bad Request with details")
    void listAnalysesWithNonAllowlistedSortFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken)
                        .param("sort", "unindexedField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property 'unindexedField'")))
                .andExpect(jsonPath("$.message", containsString("Allowed sort properties:")));
    }

    @Test
    @DisplayName("GET /api/v1/analysis with dotted path traversal fails bean validation (400 Bad Request)")
    void listAnalysesWithDottedTraversalFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken)
                        .param("sort", "uploadedFile.user.password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("Sort property contains illegal characters")));
    }

    @Test
    @DisplayName("GET /api/v1/analysis with SQL-injection-like string fails bean validation (400 Bad Request)")
    void listAnalysesWithSqlInjectionFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")
                        .header("Authorization", authToken)
                        .param("sort", "createdAt; DROP TABLE users--"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("Sort property contains illegal characters")));
    }

    // --- Packets Sorting ---

    @Test
    @DisplayName("GET /api/v1/analysis/{id}/packets with allowlisted sort succeeds (200 OK)")
    void listPacketsWithAllowlistedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/{id}/packets", analysis.getId())
                        .header("Authorization", authToken)
                        .param("sort", "packetTimestamp")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/analysis/{id}/packets with non-allowlisted sort returns 400 Bad Request")
    void listPacketsWithNonAllowlistedSortFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/{id}/packets", analysis.getId())
                        .header("Authorization", authToken)
                        .param("sort", "unknownPacketProperty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property 'unknownPacketProperty'")))
                .andExpect(jsonPath("$.message", containsString("packetNumber")));
    }

    @Test
    @DisplayName("GET /api/v1/analysis/{id}/packets with dotted path traversal returns 400 Bad Request")
    void listPacketsWithDottedTraversalFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/{id}/packets", analysis.getId())
                        .header("Authorization", authToken)
                        .param("sort", "analysis.uploadedFile.checksum"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("Sort property contains illegal characters")));
    }

    // --- Threats Sorting ---

    @Test
    @DisplayName("GET /api/v1/threats with allowlisted sort succeeds (200 OK)")
    void listThreatsWithAllowlistedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/threats")
                        .header("Authorization", authToken)
                        .param("sort", "severity")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/threats with non-allowlisted sort returns 400 Bad Request")
    void listThreatsWithNonAllowlistedSortFails() throws Exception {
        mockMvc.perform(get("/api/v1/threats")
                        .header("Authorization", authToken)
                        .param("sort", "arbitraryField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property 'arbitraryField'")))
                .andExpect(jsonPath("$.message", containsString("detectedAt")));
    }

    // --- Uploaded Files Sorting ---

    @Test
    @DisplayName("GET /api/v1/files with allowlisted sort succeeds (200 OK)")
    void listFilesWithAllowlistedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/files")
                        .header("Authorization", authToken)
                        .param("sort", "originalFileName")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/files with dotted traversal returns 400 Bad Request")
    void listFilesWithDottedTraversalFails() throws Exception {
        mockMvc.perform(get("/api/v1/files")
                        .header("Authorization", authToken)
                        .param("sort", "user.password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("Sort property contains illegal characters")));
    }

    // --- Conversations Sorting ---

    @Test
    @DisplayName("GET /api/v1/analysis/{id}/conversations with allowlisted sort succeeds (200 OK)")
    void listConversationsWithAllowlistedSortSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/{id}/conversations", analysis.getId())
                        .header("Authorization", authToken)
                        .param("sort", "bytes")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/analysis/{id}/conversations with non-allowlisted sort returns 400 Bad Request")
    void listConversationsWithNonAllowlistedSortFails() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/{id}/conversations", analysis.getId())
                        .header("Authorization", authToken)
                        .param("sort", "unsupportedConversationProperty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property 'unsupportedConversationProperty'")))
                .andExpect(jsonPath("$.message", containsString("packets")));
    }
}
