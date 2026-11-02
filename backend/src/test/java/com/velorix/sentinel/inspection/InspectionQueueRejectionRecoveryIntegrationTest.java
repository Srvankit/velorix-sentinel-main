package com.velorix.sentinel.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.velorix.sentinel.config.InspectionExecutorConfig;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.entity.ActivityLog;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.security.UserPrincipal;
import com.velorix.sentinel.service.AnalysisReaperService;
import com.velorix.sentinel.service.InspectionService;
import com.velorix.sentinel.service.impl.AnalysisReaperServiceImpl;
import com.velorix.sentinel.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
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
class InspectionQueueRejectionRecoveryIntegrationTest {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private AnalysisReaperService reaperService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @MockitoSpyBean(name = InspectionExecutorConfig.EXECUTOR_BEAN)
    private Executor inspectionExecutor;

    private User testUser;
    private UploadedFile testFile;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .firstName("Reaper")
                .lastName("Tester")
                .email("reaper." + System.nanoTime() + "@velorix.io")
                .password("hash1234567890")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        UserPrincipal principal = UserPrincipal.from(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        byte[] pcapBytes = buildSinglePacketPcap();
        String storageKey = UUID.randomUUID() + ".pcap";
        fileStorageService.store(storageKey, new ByteArrayInputStream(pcapBytes), pcapBytes.length);

        testFile = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("reaper-test.pcap")
                .storedFileName(storageKey)
                .fileSize((long) pcapBytes.length)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("chk-" + System.nanoTime())
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Abandoned PROCESSING analysis blocks subsequent inspections until reaped, then re-inspection succeeds")
    void abandonedProcessingAnalysisBlocksThenReaperUnblocks() {
        // 1. Create an abandoned PROCESSING analysis from a simulated prior crash
        Instant staleStartedAt = Instant.now().minus(Duration.ofMinutes(25));
        Analysis abandoned = analysisRepository.save(Analysis.builder()
                .uploadedFile(testFile)
                .status(AnalysisStatus.PROCESSING)
                .stage(AnalysisStage.EXTRACTING)
                .startedAt(staleStartedAt)
                .analysisVersion("detect-1.0.0")
                .build());

        // 2. Confirm another inspection attempt is rejected with HTTP 409 Conflict while active
        assertThat(analysisRepository.existsActiveForFile(testFile.getId())).isTrue();
        assertThatThrownBy(() -> inspectionService.start(testFile.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("An inspection of this capture is already running");

        // 3. Trigger periodic reaper
        int reaped = reaperService.reapTimedOutAnalyses();
        assertThat(reaped).isGreaterThanOrEqualTo(1);

        // 4. Confirm old analysis became FAILED with timeout reason
        Analysis updated = analysisRepository.findById(abandoned.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(updated.getStage()).isEqualTo(AnalysisStage.FAILED);
        assertThat(updated.getFailureReason()).contains("Inspection timed out");
        assertThat(analysisRepository.existsActiveForFile(testFile.getId())).isFalse();

        // 5. Confirm capture can be inspected again successfully
        AnalysisResponse newRun = inspectionService.start(testFile.getId());
        assertThat(newRun).isNotNull();
        assertThat(newRun.status()).isIn(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING, AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("Startup recovery marks stranded QUEUED and PROCESSING runs as FAILED and unblocks captures")
    void startupRecoveryMarksStrandedRunsFailed() {
        Analysis strandedQueued = analysisRepository.save(Analysis.builder()
                .uploadedFile(testFile)
                .status(AnalysisStatus.QUEUED)
                .stage(AnalysisStage.PREPARING)
                .analysisVersion("detect-1.0.0")
                .build());

        assertThat(analysisRepository.existsActiveForFile(testFile.getId())).isTrue();

        int recovered = reaperService.recoverStuckAnalysesOnStartup();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        Analysis reaped = analysisRepository.findById(strandedQueued.getId()).orElseThrow();
        assertThat(reaped.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(reaped.getFailureReason()).isEqualTo(AnalysisReaperServiceImpl.SERVER_RESTARTED_REASON);
        assertThat(analysisRepository.existsActiveForFile(testFile.getId())).isFalse();

        // Capture is now unblocked
        AnalysisResponse restarted = inspectionService.start(testFile.getId());
        assertThat(restarted).isNotNull();
    }

    @Test
    @DisplayName("Worker queue saturation marks analysis FAILED and throws ResourceConflictException (HTTP 409)")
    void queueRejectionMarksFailedAndThrowsConflict() {
        doThrow(new RejectedExecutionException("Inspection queue is saturated; rejecting run"))
                .when(inspectionExecutor).execute(any());

        assertThatThrownBy(() -> inspectionService.start(testFile.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Inspection queue is at capacity. Please retry shortly.");

        // Confirm analysis was persisted as FAILED and capture is not stuck
        List<Analysis> analyses = analysisRepository.findAllByUploadedFileId(testFile.getId(), null).getContent();
        assertThat(analyses).isNotEmpty();

        Analysis rejected = analyses.get(analyses.size() - 1);
        assertThat(rejected.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(rejected.getStage()).isEqualTo(AnalysisStage.FAILED);
        assertThat(rejected.getFailureReason()).isEqualTo("Inspection capacity exceeded. Please retry shortly.");
        assertThat(analysisRepository.existsActiveForFile(testFile.getId())).isFalse();

        // Verify activity log was recorded
        List<ActivityLog> logs = activityLogRepository.findAll();
        boolean hasFailureLog = logs.stream()
                .anyMatch(l -> l.getUser().getId().equals(testUser.getId())
                        && l.getActivityType() == ActivityType.ANALYSIS_FAILED
                        && l.getDescription().contains("Inspection capacity exceeded"));
        assertThat(hasFailureLog).isTrue();
    }

    private byte[] buildSinglePacketPcap() {
        ByteBuffer buf = ByteBuffer.allocate(94);
        buf.putInt(0xa1b2c3d4);          // Magic
        buf.putShort((short) 2);         // Major
        buf.putShort((short) 4);         // Minor
        buf.putInt(0);                   // Thiszone
        buf.putInt(0);                   // Sigfigs
        buf.putInt(65535);               // Snaplen
        buf.putInt(1);                   // LinkType = LINKTYPE_ETHERNET

        buf.putInt(1703980032);          // ts_sec
        buf.putInt(0);                   // ts_usec
        buf.putInt(54);                  // incl_len
        buf.putInt(54);                  // orig_len

        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
        buf.putShort((short) 0x0800);

        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) 40);
        buf.putShort((short) 0x1234);
        buf.putShort((short) 0x4000);
        buf.put((byte) 64);
        buf.put((byte) 6);
        buf.putShort((short) 0);
        buf.put(new byte[]{10, 0, 0, 5});
        buf.put(new byte[]{10, 0, 0, 9});

        buf.putShort((short) 40000);
        buf.putShort((short) 80);
        buf.putInt(1);
        buf.putInt(0);
        buf.putShort((short) 0x5002);
        buf.putShort((short) 4096);
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        return buf.array();
    }
}
