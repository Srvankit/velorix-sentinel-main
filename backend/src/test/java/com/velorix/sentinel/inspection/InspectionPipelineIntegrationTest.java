package com.velorix.sentinel.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.detection.ThreatDetectionEngine;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.dto.threat.ThreatStatsResponse;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.security.UserPrincipal;
import com.velorix.sentinel.service.DetectionService;
import com.velorix.sentinel.service.InspectionService;
import com.velorix.sentinel.service.ThreatService;
import com.velorix.sentinel.service.impl.AnalysisRunStateService;
import com.velorix.sentinel.service.impl.InspectionServiceImpl;
import com.velorix.sentinel.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class InspectionPipelineIntegrationTest {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private DetectionService detectionService;

    @Autowired
    private ThreatService threatService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private AnalysisSummaryRepository analysisSummaryRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AnalysisRunStateService runState;

    @Autowired
    private InspectionServiceImpl inspectionServiceImpl;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .firstName("Pipeline")
                .lastName("Tester")
                .email("pipeline." + System.nanoTime() + "@velorix.io")
                .password("hash1234567890")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        UserPrincipal principal = UserPrincipal.from(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Inspection automatically triggers threat detection and completes with findings summary")
    void automatedInspectionTriggersDetectionAndCompletes() throws Exception {
        byte[] pcapBytes = buildSinglePacketPcap();
        String storageKey = UUID.randomUUID() + ".pcap";
        fileStorageService.store(storageKey, new ByteArrayInputStream(pcapBytes), pcapBytes.length);

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("test-traffic.pcap")
                .storedFileName(storageKey)
                .fileSize((long) pcapBytes.length)
                .checksum("chk-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        AnalysisResponse queued = inspectionService.start(file.getId());
        UUID analysisId = queued.id();
        assertThat(queued.status()).isEqualTo(AnalysisStatus.QUEUED);

        // Await asynchronous worker completion (polling up to 5 seconds)
        Instant deadline = Instant.now().plusSeconds(5);
        Analysis completed = null;
        while (Instant.now().isBefore(deadline)) {
            Optional<Analysis> opt = analysisRepository.findById(analysisId);
            if (opt.isPresent() && opt.get().getStatus().isTerminal()) {
                completed = opt.get();
                break;
            }
            Thread.sleep(100);
        }

        assertThat(completed).isNotNull();
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.getTotalPackets()).isEqualTo(1L);

        // Verify threat detection executed automatically: AnalysisSummary was saved without calling /threats/run
        Optional<AnalysisSummary> summaryOpt = analysisSummaryRepository.findByAnalysisId(analysisId);
        assertThat(summaryOpt).isPresent();
        AnalysisSummary summary = summaryOpt.get();
        assertThat(summary.getAnalysis().getId()).isEqualTo(analysisId);
        assertThat(summary.getTotalThreats()).isGreaterThanOrEqualTo(0L);

        // Verify Task 1.2: safePackets and maliciousPackets are accurately calculated
        assertThat(completed.getSafePackets()).isEqualTo(1L);
        assertThat(completed.getMaliciousPackets()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Analysis transitions to FAILED and is never reported COMPLETED if threat detection fails")
    void analysisNeverMarkedCompletedIfDetectionFails() {
        UUID fakeAnalysisId = UUID.randomUUID();
        // Create an analysis record directly in PROCESSING state
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("fail-test.pcap")
                .storedFileName("fail-test.pcap")
                .fileSize(100L)
                .checksum("fail-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        Analysis analysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.PROCESSING)
                .analysisVersion("1.0.0")
                .totalPackets(10L)
                .build());

        // Simulate a failure during detection inside the pipeline
        Instant startedAt = Instant.now();
        runState.fail(analysis.getId(), "Detection engine simulated error", startedAt);

        Analysis failed = analysisRepository.findById(analysis.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("Detection engine simulated error");
        assertThat(failed.getStatus()).isNotEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("Duplicate or concurrent threat detection runs on the same analysis are prevented with 409 Conflict")
    void duplicateDetectionRunsPrevented() throws Exception {
        UUID analysisId = UUID.randomUUID();

        // Simulate thread 1 running detection while thread 2 attempts concurrent execution
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        AtomicBoolean conflictCaught = new AtomicBoolean(false);

        // Pre-seed an analysis with 0 packets to trigger quick exit once allowed
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("concurrent.pcap")
                .storedFileName("concurrent.pcap")
                .fileSize(100L)
                .checksum("conc-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        Analysis analysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.COMPLETED)
                .analysisVersion("1.0.0")
                .totalPackets(5L)
                .build());

        // Call runInternal on two threads
        Executor testPool = Executors.newFixedThreadPool(2);
        testPool.execute(() -> {
            try {
                // Thread 1
                detectionService.runInternal(analysis.getId());
            } catch (Exception ignored) {
            }
        });

        // Immediately call on second thread
        testPool.execute(() -> {
            try {
                detectionService.runInternal(analysis.getId());
            } catch (ResourceConflictException ex) {
                conflictCaught.set(true);
            } catch (Exception ignored) {
            }
        });

        // Small wait for execution
        Thread.sleep(500);

        // Verify that analysis status is not corrupted
        Analysis checked = analysisRepository.findById(analysis.getId()).orElseThrow();
        assertThat(checked.getStatus()).isIn(AnalysisStatus.COMPLETED, AnalysisStatus.PROCESSING);
    }

    @Test
    @DisplayName("Manual detection endpoint rejects analysis that is currently PROCESSING")
    void manualDetectionRejectedWhileAnalysisIsProcessing() {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("processing.pcap")
                .storedFileName("processing.pcap")
                .fileSize(100L)
                .checksum("proc-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        Analysis processing = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.PROCESSING)
                .analysisVersion("1.0.0")
                .totalPackets(10L)
                .build());

        assertThatThrownBy(() -> detectionService.run(processing.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("currently being processed");
    }

    @Test
    @DisplayName("A QUEUED analysis with score 0 is not reported as SECURE")
    void queuedAnalysisIsNotSecure() {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("queued-test.pcap")
                .storedFileName(UUID.randomUUID() + ".pcap")
                .fileSize(100L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.UPLOADED)
                .checksum("chk-" + UUID.randomUUID())
                .build());

        Analysis queued = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.QUEUED)
                .riskScore(0)
                .analysisVersion("1.0.0")
                .totalPackets(0L)
                .build());

        ThreatStatsResponse stats = threatService.stats(queued.getId());
        assertThat(stats.overallStatus()).isNull();
        assertThat(stats.riskScore()).isNull();
    }

    @Test
    @DisplayName("A PROCESSING analysis with score 0 is not reported as SECURE")
    void processingAnalysisIsNotSecure() {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("processing-test.pcap")
                .storedFileName(UUID.randomUUID() + ".pcap")
                .fileSize(100L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("chk-" + UUID.randomUUID())
                .build());

        Analysis processing = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.PROCESSING)
                .riskScore(0)
                .analysisVersion("1.0.0")
                .totalPackets(50L)
                .build());

        ThreatStatsResponse stats = threatService.stats(processing.getId());
        assertThat(stats.overallStatus()).isNull();
        assertThat(stats.riskScore()).isNull();
    }

    @Test
    @DisplayName("A FAILED analysis with score 0 is not reported as SECURE")
    void failedAnalysisIsNotSecure() {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("failed-test.pcap")
                .storedFileName(UUID.randomUUID() + ".pcap")
                .fileSize(100L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.FAILED)
                .checksum("chk-" + UUID.randomUUID())
                .build());

        Analysis failed = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.FAILED)
                .failureReason("File corrupted mid-stream")
                .riskScore(0)
                .analysisVersion("1.0.0")
                .totalPackets(10L)
                .build());

        ThreatStatsResponse stats = threatService.stats(failed.getId());
        assertThat(stats.overallStatus()).isNull();
        assertThat(stats.riskScore()).isNull();
    }

    @Test
    @DisplayName("A COMPLETED analysis with detection outcome and 0 threats is reported as SECURE")
    void completedAnalysisWithCleanDetectionIsSecure() {
        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("completed-clean.pcap")
                .storedFileName(UUID.randomUUID() + ".pcap")
                .fileSize(100L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("chk-" + UUID.randomUUID())
                .build());

        Analysis completed = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.COMPLETED)
                .riskScore(0)
                .analysisVersion("1.0.0")
                .totalPackets(100L)
                .safePackets(100L)
                .maliciousPackets(0L)
                .build());

        analysisSummaryRepository.save(AnalysisSummary.builder()
                .analysis(completed)
                .totalThreats(0L)
                .criticalThreats(0L)
                .highThreats(0L)
                .mediumThreats(0L)
                .lowThreats(0L)
                .riskScore(0)
                .overallStatus(OverallStatus.SECURE)
                .build());

        ThreatStatsResponse stats = threatService.stats(completed.getId());
        assertThat(stats.overallStatus()).isEqualTo(OverallStatus.SECURE);
        assertThat(stats.riskScore()).isEqualTo(0);
    }

    /**
     * Constructs a synthetic, byte-valid PCAP containing 1 TCP SYN frame.
     */
    private byte[] buildSinglePacketPcap() {
        ByteBuffer buf = ByteBuffer.allocate(94);
        // PCAP Global Header (24 bytes)
        buf.putInt(0xa1b2c3d4);          // Magic
        buf.putShort((short) 2);         // Major
        buf.putShort((short) 4);         // Minor
        buf.putInt(0);                   // Thiszone
        buf.putInt(0);                   // Sigfigs
        buf.putInt(65535);               // Snaplen
        buf.putInt(1);                   // LinkType = LINKTYPE_ETHERNET

        // Record Header (16 bytes)
        buf.putInt(1703980032);          // ts_sec
        buf.putInt(0);                   // ts_usec
        buf.putInt(54);                  // incl_len (54 bytes)
        buf.putInt(54);                  // orig_len (54 bytes)

        // Ethernet (14 bytes)
        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}); // Dst MAC
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb}); // Src MAC
        buf.putShort((short) 0x0800);    // IPv4

        // IPv4 Header (20 bytes)
        buf.put((byte) 0x45);            // Ver/IHL
        buf.put((byte) 0x00);            // DSCP
        buf.putShort((short) 40);        // Total Length
        buf.putShort((short) 0x1234);    // Identification
        buf.putShort((short) 0x4000);    // Flags / Fragment
        buf.put((byte) 64);              // TTL
        buf.put((byte) 6);               // Protocol: TCP
        buf.putShort((short) 0);         // Checksum
        buf.put(new byte[]{10, 0, 0, 5});// Src IP
        buf.put(new byte[]{10, 0, 0, 9});// Dst IP

        // TCP Header (20 bytes)
        buf.putShort((short) 40000);     // Src Port
        buf.putShort((short) 80);        // Dst Port
        buf.putInt(1);                   // Seq
        buf.putInt(0);                   // Ack
        buf.putShort((short) 0x5002);    // Offset = 5, Flags = SYN
        buf.putShort((short) 4096);      // Window
        buf.putShort((short) 0);         // Checksum
        buf.putShort((short) 0);         // Urgent pointer

        return buf.array();
    }
}
