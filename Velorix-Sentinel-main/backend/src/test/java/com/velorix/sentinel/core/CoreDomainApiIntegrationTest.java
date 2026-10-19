package com.velorix.sentinel.core;

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
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that the core security endpoints are wired end to end - database, service,
 * mapper, controller - and that every read is scoped to the calling account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreDomainApiIntegrationTest {

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

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analysis")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/threats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/files")).andExpect(status().isUnauthorized());
    }

    @Test
    void servesOwnedCapturesAnalysesThreatsAndPackets() throws Exception {
        User owner = persistUser("owner");
        Analysis analysis = seedAnalysis(owner);
        String token = login(owner.getEmail());

        mockMvc.perform(get("/api/v1/files").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].originalFileName").value("capture.pcap"));

        mockMvc.perform(get("/api/v1/analysis").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value(AnalysisStatus.COMPLETED.name()));

        mockMvc.perform(get("/api/v1/analysis/" + analysis.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.totalPackets").value(1))
                .andExpect(jsonPath("$.data.uploadedFile.originalFileName").value("capture.pcap"))
                .andExpect(jsonPath("$.data.summary.overallStatus").value(OverallStatus.HIGH_RISK.name()));

        mockMvc.perform(get("/api/v1/analysis/" + analysis.getId() + "/packets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sourceIp").value("10.0.0.7"));

        mockMvc.perform(get("/api/v1/threats?severity=CRITICAL").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].threatType").value(ThreatType.PORT_SCAN.name()));

        mockMvc.perform(get("/api/v1/threats?severity=LOW").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void hidesDataOwnedByAnotherAccount() throws Exception {
        User owner = persistUser("victim");
        Analysis analysis = seedAnalysis(owner);
        User stranger = persistUser("stranger");
        String token = login(stranger.getEmail());

        mockMvc.perform(get("/api/v1/analysis").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/analysis/" + analysis.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/analysis/" + analysis.getId() + "/packets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private User persistUser(String prefix) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("Analyst")
                .email("%s.%s@velorix.io".formatted(prefix, System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());
    }

    private Analysis seedAnalysis(User owner) {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(owner)
                .originalFileName("capture.pcap")
                .storedFileName("stored-%s.pcap".formatted(System.nanoTime()))
                .fileSize(2048L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("a".repeat(64))
                .build());

        Analysis analysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.COMPLETED)
                .startedAt(Instant.now().minusSeconds(30))
                .completedAt(Instant.now())
                .duration(30_000L)
                .totalPackets(1L)
                .maliciousPackets(1L)
                .safePackets(0L)
                .riskScore(82)
                .analysisVersion("1.0.0")
                .build());

        packetRepository.save(Packet.builder()
                .analysis(analysis)
                .packetNumber(1L)
                .timestamp(Instant.now())
                .sourceIp("10.0.0.7")
                .destinationIp("10.0.0.9")
                .sourcePort(44_321)
                .destinationPort(22)
                .protocol(Protocol.TCP)
                .packetLength(74)
                .tcpFlags("SYN")
                .payloadSize(0)
                .suspicious(true)
                .build());

        threatRepository.save(Threat.builder()
                .analysis(analysis)
                .threatType(ThreatType.PORT_SCAN)
                .severity(ThreatSeverity.CRITICAL)
                .confidenceScore(0.93d)
                .title("Horizontal port scan detected")
                .description("A single host probed 512 ports across the subnet in under a minute.")
                .recommendation("Block the source address and review perimeter firewall rules.")
                .detectedAt(Instant.now())
                .build());

        analysisSummaryRepository.save(AnalysisSummary.builder()
                .analysis(analysis)
                .totalThreats(1L)
                .criticalThreats(1L)
                .riskScore(82)
                .overallStatus(OverallStatus.HIGH_RISK)
                .build());

        return analysis;
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
