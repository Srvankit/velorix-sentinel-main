package com.velorix.sentinel.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.Packet;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PacketThreatClassifierTest {

    @Autowired
    private PacketThreatClassifier classifier;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private PacketRepository packetRepository;

    private User testUser;
    private Analysis testAnalysis;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("analyst." + System.nanoTime() + "@velorix.io")
                .password("TestPassword123!")
                .firstName("Test")
                .lastName("Analyst")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("traffic.pcap")
                .storedFileName("traffic-" + System.nanoTime() + ".pcap")
                .fileSize(1024L)
                .checksum("chk-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        testAnalysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.PROCESSING)
                .analysisVersion("1.0.0")
                .totalPackets(5L)
                .build());
        analysisRepository.flush();
    }

    private void insertPacket(UUID analysisId, long packetNumber, String proto, String netProto, boolean malformed) {
        jdbcTemplate.update("""
                INSERT INTO packets (
                    id, analysis_id, packet_number, packet_timestamp,
                    source_ip, destination_ip, source_port, destination_port,
                    protocol, network_protocol, packet_length, captured_length, payload_size,
                    is_suspicious, is_malformed, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                analysisId,
                packetNumber,
                Timestamp.from(Instant.now()),
                "192.168.1.10",
                "10.0.0.1",
                4444,
                80,
                proto,
                netProto,
                64,
                64,
                20,
                false,
                malformed,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                0L);
    }

    private ThreatCandidate dummyFinding(String ruleId, ThreatSeverity severity, List<Long> sampleFrames) {
        RuleMetadata meta = new RuleMetadata(
                ruleId, ruleId, "1.0", "description",
                ThreatType.PORT_SCAN, severity, "T1046", "Network Service Scanning",
                "evidence", "recommendation");
        return new ThreatCandidate(
                meta, ThreatType.PORT_SCAN, severity, 0.9,
                "Threat detected", "description", "evidence", "rec",
                "192.168.1.10", "10.0.0.1", null, sampleFrames.size(),
                sampleFrames, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("A packet associated with a genuine detection finding is marked suspicious; unflagged packets are handled correctly")
    void packetWithFindingMarkedSuspiciousAndUnrelatedMarkedSafe() {
        UUID aid = testAnalysis.getId();
        insertPacket(aid, 1L, "TCP", "IPV4", false);
        insertPacket(aid, 2L, "TCP", "IPV4", false);
        insertPacket(aid, 3L, "UDP", "IPV4", false);

        ThreatCandidate finding = dummyFinding("syn-scan", ThreatSeverity.HIGH, List.of(1L));

        PacketCounters counters = classifier.classifyAndCount(aid, List.of(finding));

        // Verify packet-level indicators in DB
        List<Packet> packets = packetRepository.findAll().stream()
                .filter(p -> p.getAnalysis().getId().equals(aid))
                .toList();
        Packet packet1 = packets.stream().filter(p -> p.getPacketNumber() == 1L).findFirst().orElseThrow();
        Packet packet2 = packets.stream().filter(p -> p.getPacketNumber() == 2L).findFirst().orElseThrow();
        Packet packet3 = packets.stream().filter(p -> p.getPacketNumber() == 3L).findFirst().orElseThrow();

        assertThat(packet1.isSuspicious()).isTrue();
        assertThat(packet2.isSuspicious()).isFalse();
        assertThat(packet3.isSuspicious()).isFalse();

        // Verify counters
        assertThat(counters.totalPackets()).isEqualTo(3L);
        assertThat(counters.maliciousPackets()).isEqualTo(1L);
        assertThat(counters.safePackets()).isEqualTo(2L);
        assertThat(counters.unclassifiedPackets()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Multiple matching detection rules do not inflate malicious packet counts")
    void multipleMatchingRulesDoNotInflateCounts() {
        UUID aid = testAnalysis.getId();
        insertPacket(aid, 1L, "TCP", "IPV4", false);
        insertPacket(aid, 2L, "TCP", "IPV4", false);
        insertPacket(aid, 3L, "TCP", "IPV4", false);

        // Rule 1 flags packets 1 and 2
        ThreatCandidate finding1 = dummyFinding("syn-scan", ThreatSeverity.HIGH, List.of(1L, 2L));
        // Rule 2 also flags packet 2, plus packet 3
        ThreatCandidate finding2 = dummyFinding("port-scan", ThreatSeverity.HIGH, List.of(2L, 3L));

        PacketCounters counters = classifier.classifyAndCount(aid, List.of(finding1, finding2));

        assertThat(counters.totalPackets()).isEqualTo(3L);
        // Packets 1, 2, 3 should each be counted exactly once
        assertThat(counters.maliciousPackets()).isEqualTo(3L);
        assertThat(counters.safePackets()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Safe and malicious counters are accurate for a mixed packet capture")
    void mixedCaptureCalculatesCountersAccurately() {
        UUID aid = testAnalysis.getId();
        insertPacket(aid, 1L, "TCP", "IPV4", false); // malicious
        insertPacket(aid, 2L, "TCP", "IPV4", false); // malicious
        insertPacket(aid, 3L, "TCP", "IPV4", false); // safe
        insertPacket(aid, 4L, "UDP", "IPV4", false); // safe
        insertPacket(aid, 5L, "ICMP", "IPV4", false); // safe
        insertPacket(aid, 6L, "OTHER", "OTHER", true); // malformed unclassified

        ThreatCandidate finding = dummyFinding("dos-flood", ThreatSeverity.CRITICAL, List.of(1L, 2L));

        PacketCounters counters = classifier.classifyAndCount(aid, List.of(finding));

        assertThat(counters.totalPackets()).isEqualTo(6L);
        assertThat(counters.maliciousPackets()).isEqualTo(2L);
        assertThat(counters.safePackets()).isEqualTo(3L);
        assertThat(counters.unclassifiedPackets()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Unknown or unclassifiable packets are never incorrectly reported as safe")
    void unknownOrMalformedPacketsNeverReportedAsSafe() {
        UUID aid = testAnalysis.getId();
        insertPacket(aid, 1L, "TCP", "IPV4", false); // valid safe
        insertPacket(aid, 2L, "OTHER", "OTHER", true); // malformed
        insertPacket(aid, 3L, "OTHER", "OTHER", false); // unclassified protocol

        // No findings detected (clean capture)
        PacketCounters counters = classifier.classifyAndCount(aid, List.of());

        assertThat(counters.totalPackets()).isEqualTo(3L);
        assertThat(counters.maliciousPackets()).isEqualTo(0L);
        // Only packet 1 is positively safe; packets 2 and 3 must not be classified as safe
        assertThat(counters.safePackets()).isEqualTo(1L);
        assertThat(counters.unclassifiedPackets()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Re-running detection resets previous suspicious flags cleanly and re-applies current evidence")
    void rerunResetsPreviousSuspiciousFlags() {
        UUID aid = testAnalysis.getId();
        insertPacket(aid, 1L, "TCP", "IPV4", false);
        insertPacket(aid, 2L, "TCP", "IPV4", false);

        // Run 1: both packets flagged
        classifier.classifyAndCount(aid, List.of(dummyFinding("rule-a", ThreatSeverity.HIGH, List.of(1L, 2L))));
        assertThat(packetRepository.countByAnalysisIdAndSuspiciousTrue(aid)).isEqualTo(2L);

        // Run 2: finding now only flags packet 1
        PacketCounters counters = classifier.classifyAndCount(aid, List.of(dummyFinding("rule-a", ThreatSeverity.HIGH, List.of(1L))));
        assertThat(counters.maliciousPackets()).isEqualTo(1L);
        assertThat(counters.safePackets()).isEqualTo(1L);
        assertThat(packetRepository.countByAnalysisIdAndSuspiciousTrue(aid)).isEqualTo(1L);
    }
}
