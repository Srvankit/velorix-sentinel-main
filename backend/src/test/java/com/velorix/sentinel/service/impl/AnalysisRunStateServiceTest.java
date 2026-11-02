package com.velorix.sentinel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.service.ActivityLogService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisRunStateServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AnalysisSummaryRepository analysisSummaryRepository;

    @Mock
    private PacketRepository packetRepository;

    @Mock
    private ActivityLogService activityLogService;

    private AnalysisRunStateService runStateService;

    private User testUser;
    private UploadedFile testFile;

    @BeforeEach
    void setUp() {
        runStateService = new AnalysisRunStateService(
                analysisRepository,
                analysisSummaryRepository,
                packetRepository,
                activityLogService);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("analysistest@velorix.io")
                .build();

        testFile = UploadedFile.builder()
                .id(UUID.randomUUID())
                .originalFileName("traffic.pcap")
                .user(testUser)
                .build();
    }

    @Test
    @DisplayName("fail() with null startedAt does not throw NullPointerException and sets duration to 0")
    void failWithNullStartedAtDoesNotThrow() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .uploadedFile(testFile)
                .status(AnalysisStatus.QUEUED)
                .stage(AnalysisStage.PREPARING)
                .startedAt(null)
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        assertThatCode(() -> runStateService.fail(analysisId, "Server restarted during processing; please re-run.", null))
                .doesNotThrowAnyException();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getStage()).isEqualTo(AnalysisStage.FAILED);
        assertThat(analysis.getCompletedAt()).isNotNull();
        assertThat(analysis.getDuration()).isEqualTo(0L);
        assertThat(analysis.getFailureReason()).isEqualTo("Server restarted during processing; please re-run.");

        verify(analysisRepository).save(analysis);
        verify(activityLogService).failure(
                eq(testUser),
                eq(ActivityType.ANALYSIS_FAILED),
                eq("Server restarted during processing; please re-run."));
    }

    @Test
    @DisplayName("fail() with valid startedAt calculates positive duration")
    void failWithValidStartedAtCalculatesDuration() {
        UUID analysisId = UUID.randomUUID();
        Instant startedAt = Instant.now().minus(Duration.ofSeconds(45));
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .uploadedFile(testFile)
                .status(AnalysisStatus.PROCESSING)
                .stage(AnalysisStage.EXTRACTING)
                .startedAt(startedAt)
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        runStateService.fail(analysisId, "Inspection timed out after exceeding 15 minutes; please re-run.", startedAt);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getStage()).isEqualTo(AnalysisStage.FAILED);
        assertThat(analysis.getCompletedAt()).isNotNull();
        assertThat(analysis.getDuration()).isGreaterThanOrEqualTo(45000L);
        assertThat(analysis.getFailureReason()).contains("Inspection timed out");

        verify(analysisRepository).save(analysis);
        verify(activityLogService).failure(
                eq(testUser),
                eq(ActivityType.ANALYSIS_FAILED),
                eq("Inspection timed out after exceeding 15 minutes; please re-run."));
    }

    @Test
    @DisplayName("createQueued() persists Analysis with QUEUED status and logs ANALYSIS_STARTED")
    void createQueuedPersistsAndLogs() {
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);
        when(analysisRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Analysis created = runStateService.createQueued(testFile, "detect-1.0.0");

        Analysis saved = captor.getValue();
        assertThat(saved.getUploadedFile()).isEqualTo(testFile);
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(saved.getStage()).isEqualTo(AnalysisStage.PREPARING);
        assertThat(saved.getAnalysisVersion()).isEqualTo("detect-1.0.0");

        verify(activityLogService).success(
                eq(testUser),
                eq(ActivityType.ANALYSIS_STARTED),
                eq("Inspection queued for capture traffic.pcap"));
    }
}
