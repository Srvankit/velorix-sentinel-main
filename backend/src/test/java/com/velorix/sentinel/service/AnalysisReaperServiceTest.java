package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.service.impl.AnalysisReaperServiceImpl;
import com.velorix.sentinel.service.impl.AnalysisRunStateService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisReaperServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AnalysisRunStateService runState;

    private InspectionProperties properties;
    private AnalysisReaperServiceImpl reaperService;

    @BeforeEach
    void setUp() {
        properties = new InspectionProperties(
                "detect-1.0.0",
                1000,
                5_000_000L,
                Duration.ofMinutes(15),
                2000,
                2,
                50);
        reaperService = new AnalysisReaperServiceImpl(analysisRepository, runState, properties);
    }

    @Nested
    @DisplayName("Startup Recovery")
    class StartupRecoveryTests {

        @Test
        @DisplayName("Recovers QUEUED and PROCESSING analyses left by previous application instance")
        void recoversQueuedAndProcessingAnalyses() {
            UUID queuedId = UUID.randomUUID();
            UUID processingId = UUID.randomUUID();
            Instant startedAt = Instant.now().minus(Duration.ofMinutes(5));

            Analysis queued = Analysis.builder()
                    .id(queuedId)
                    .status(AnalysisStatus.QUEUED)
                    .startedAt(null)
                    .build();

            Analysis processing = Analysis.builder()
                    .id(processingId)
                    .status(AnalysisStatus.PROCESSING)
                    .startedAt(startedAt)
                    .build();

            when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING)))
                    .thenReturn(List.of(queued, processing));

            int recovered = reaperService.recoverStuckAnalysesOnStartup();

            assertThat(recovered).isEqualTo(2);

            verify(runState).fail(
                    eq(queuedId),
                    eq(AnalysisReaperServiceImpl.SERVER_RESTARTED_REASON),
                    isNull());

            verify(runState).fail(
                    eq(processingId),
                    eq(AnalysisReaperServiceImpl.SERVER_RESTARTED_REASON),
                    eq(startedAt));
        }

        @Test
        @DisplayName("Returns 0 when no stranded analyses exist on startup")
        void returnsZeroWhenNoneStranded() {
            when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING)))
                    .thenReturn(Collections.emptyList());

            int recovered = reaperService.recoverStuckAnalysesOnStartup();

            assertThat(recovered).isEqualTo(0);
            verify(runState, never()).fail(any(), any(), any());
        }

        @Test
        @DisplayName("Continues recovering remaining runs even if one individual run fails")
        void continuesOnIndividualFailure() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            Analysis a1 = Analysis.builder().id(id1).status(AnalysisStatus.QUEUED).build();
            Analysis a2 = Analysis.builder().id(id2).status(AnalysisStatus.PROCESSING).build();

            when(analysisRepository.findByStatusIn(List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING)))
                    .thenReturn(List.of(a1, a2));

            org.mockito.Mockito.doThrow(new RuntimeException("DB locked"))
                    .when(runState).fail(eq(id1), any(), any());

            int recovered = reaperService.recoverStuckAnalysesOnStartup();

            assertThat(recovered).isEqualTo(1);
            verify(runState).fail(eq(id2), eq(AnalysisReaperServiceImpl.SERVER_RESTARTED_REASON), any());
        }
    }

    @Nested
    @DisplayName("Periodic Timeout Sweep")
    class PeriodicTimeoutSweepTests {

        @Test
        @DisplayName("Reaps PROCESSING analyses that exceeded the maximum duration")
        void reapsTimedOutAnalyses() {
            UUID timedOutId = UUID.randomUUID();
            Instant startedAt = Instant.now().minus(Duration.ofMinutes(20));

            Analysis timedOut = Analysis.builder()
                    .id(timedOutId)
                    .status(AnalysisStatus.PROCESSING)
                    .startedAt(startedAt)
                    .build();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            when(analysisRepository.findStuckInStatusBefore(eq(AnalysisStatus.PROCESSING), cutoffCaptor.capture()))
                    .thenReturn(List.of(timedOut));

            int reaped = reaperService.reapTimedOutAnalyses();

            assertThat(reaped).isEqualTo(1);
            assertThat(cutoffCaptor.getValue()).isBeforeOrEqualTo(Instant.now().minus(Duration.ofMinutes(15)));

            verify(runState).fail(
                    eq(timedOutId),
                    eq("Inspection timed out after exceeding 15 minutes; please re-run."),
                    eq(startedAt));
        }

        @Test
        @DisplayName("Does not reap when no runs have exceeded maximum duration")
        void doesNotReapWhenNoneTimedOut() {
            when(analysisRepository.findStuckInStatusBefore(eq(AnalysisStatus.PROCESSING), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            int reaped = reaperService.reapTimedOutAnalyses();

            assertThat(reaped).isEqualTo(0);
            verify(runState, never()).fail(any(), any(), any());
        }
    }
}
