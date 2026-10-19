package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.inspection.InspectionProgress;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.service.ActivityLogService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the persisted state transitions of an inspection run.
 *
 * <p>Deliberately a separate bean from the orchestrator. The pipeline calls these methods
 * from a worker thread and needs each one to commit on its own; a self-invocation inside
 * the orchestrator would bypass the transactional proxy entirely and silently run every
 * checkpoint outside a transaction.</p>
 */
@Service
public class AnalysisRunStateService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunStateService.class);
    private static final String RESOURCE = "Analysis";
    private static final int MAX_FAILURE_REASON_LENGTH = 512;

    private final AnalysisRepository analysisRepository;
    private final PacketRepository packetRepository;
    private final ActivityLogService activityLogService;

    public AnalysisRunStateService(
            AnalysisRepository analysisRepository,
            PacketRepository packetRepository,
            ActivityLogService activityLogService) {
        this.analysisRepository = analysisRepository;
        this.packetRepository = packetRepository;
        this.activityLogService = activityLogService;
    }

    /** Moves the run into {@code PROCESSING} and clears any frames of a previous attempt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID analysisId, Instant startedAt) {
        Analysis analysis = require(analysisId);
        int removed = packetRepository.deleteByAnalysisId(analysisId);
        if (removed > 0) {
            log.debug("Cleared {} frames from the previous attempt of inspection {}", removed, analysisId);
        }
        analysis.setStatus(AnalysisStatus.PROCESSING);
        analysis.setStage(AnalysisStage.PREPARING);
        analysis.setStartedAt(startedAt);
        analysis.setCompletedAt(null);
        analysis.setDuration(null);
        analysis.setProgressPercent(0);
        analysis.setProcessedPackets(0L);
        analysis.setMalformedPackets(0L);
        analysis.setTotalPackets(0L);
        analysis.setFailureReason(null);
    }

    /** Checkpoint write; intentionally cheap so it can run every few thousand frames. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProgress(UUID analysisId, InspectionProgress progress) {
        analysisRepository.findById(analysisId).ifPresent(analysis -> {
            analysis.setStage(progress.stage());
            analysis.setProcessedPackets(progress.processedPackets());
            analysis.setMalformedPackets(progress.malformedPackets());
            analysis.setProgressPercent(progress.progressPercent());
        });
    }

    /** Finalises a successful run and computes the aggregates that are costly to derive. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID analysisId, InspectionProgress progress, Instant startedAt) {
        Analysis analysis = require(analysisId);
        Instant completedAt = Instant.now();

        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setStage(AnalysisStage.COMPLETED);
        analysis.setCompletedAt(completedAt);
        analysis.setDuration(Duration.between(startedAt, completedAt).toMillis());
        analysis.setProgressPercent(100);
        analysis.setTotalPackets(progress.processedPackets());
        analysis.setProcessedPackets(progress.processedPackets());
        analysis.setMalformedPackets(progress.malformedPackets());
        analysis.setTruncated(progress.truncated());
        analysis.setAveragePacketSize(progress.averagePacketSize());
        analysis.setCaptureStartedAt(progress.firstFrameAt());
        analysis.setCaptureEndedAt(progress.lastFrameAt());
        analysis.setCaptureDuration(progress.captureDurationMillis());

        // Distinct cardinality is expensive, so it is computed exactly once - here - and
        // read back from the row by every subsequent summary request.
        analysis.setUniqueSourceIps(packetRepository.countDistinctSourceIps(analysisId));
        analysis.setUniqueDestinationIps(packetRepository.countDistinctDestinationIps(analysisId));

        activityLogService.success(
                analysis.getUploadedFile().getUser(),
                ActivityType.ANALYSIS_COMPLETED,
                "Inspection completed with %d packets".formatted(progress.processedPackets()));
    }

    /**
     * Records a terminal failure. A run that dies silently and stays {@code PROCESSING}
     * forever is the worst outcome for a user watching the stepper, so this must always
     * be reachable.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID analysisId, String reason, Instant startedAt) {
        analysisRepository.findById(analysisId).ifPresent(analysis -> {
            Instant completedAt = Instant.now();
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setStage(AnalysisStage.FAILED);
            analysis.setCompletedAt(completedAt);
            analysis.setDuration(Duration.between(startedAt, completedAt).toMillis());
            analysis.setFailureReason(truncate(reason));

            activityLogService.failure(
                    analysis.getUploadedFile().getUser(),
                    ActivityType.ANALYSIS_FAILED,
                    truncate(reason));
        });
    }

    private Analysis require(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown failure";
        }
        return value.length() <= MAX_FAILURE_REASON_LENGTH ? value : value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
