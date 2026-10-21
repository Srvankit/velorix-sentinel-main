package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.detection.DetectionOutcome;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.inspection.InspectionProgress;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
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
    private final AnalysisSummaryRepository analysisSummaryRepository;
    private final PacketRepository packetRepository;
    private final ActivityLogService activityLogService;

    public AnalysisRunStateService(
            AnalysisRepository analysisRepository,
            AnalysisSummaryRepository analysisSummaryRepository,
            PacketRepository packetRepository,
            ActivityLogService activityLogService) {
        this.analysisRepository = analysisRepository;
        this.analysisSummaryRepository = analysisSummaryRepository;
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

    /**
     * Persists the packet inspection metrics and aggregates while keeping the analysis
     * in {@code PROCESSING} so detection can follow before completion.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInspectionResults(UUID analysisId, InspectionProgress progress) {
        Analysis analysis = require(analysisId);
        analysis.setStage(AnalysisStage.SAVING);
        analysis.setTotalPackets(progress.processedPackets());
        analysis.setProcessedPackets(progress.processedPackets());
        analysis.setMalformedPackets(progress.malformedPackets());
        analysis.setTruncated(progress.truncated());
        analysis.setAveragePacketSize(progress.averagePacketSize());
        analysis.setCaptureStartedAt(progress.firstFrameAt());
        analysis.setCaptureEndedAt(progress.lastFrameAt());
        analysis.setCaptureDuration(progress.captureDurationMillis());
        analysis.setUniqueSourceIps(packetRepository.countDistinctSourceIps(analysisId));
        analysis.setUniqueDestinationIps(packetRepository.countDistinctDestinationIps(analysisId));
        analysisRepository.save(analysis);
    }

    /**
     * Persists threat detection findings summary and risk score in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDetectionOutcome(UUID analysisId, DetectionOutcome outcome) {
        Analysis analysis = require(analysisId);
        AnalysisSummary summary = analysisSummaryRepository.findByAnalysisId(analysisId)
                .orElseGet(() -> AnalysisSummary.builder().analysis(analysis).build());
        summary.setAnalysis(analysis);
        summary.setTotalThreats(outcome.totalThreats());
        summary.setCriticalThreats(outcome.criticalThreats());
        summary.setHighThreats(outcome.highThreats());
        summary.setMediumThreats(outcome.mediumThreats());
        summary.setLowThreats(outcome.lowThreats());
        summary.setRiskScore(outcome.riskScore());
        summary.setOverallStatus(outcome.overallStatus());
        analysisSummaryRepository.save(summary);

        analysis.setRiskScore(outcome.riskScore());
        analysis.setMaliciousPackets(outcome.maliciousPackets());
        analysis.setSafePackets(outcome.safePackets());
        analysisRepository.save(analysis);
    }

    /** Finalises a successful run after both inspection and detection have finished. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID analysisId, Instant startedAt) {
        Analysis analysis = require(analysisId);
        Instant completedAt = Instant.now();

        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setStage(AnalysisStage.COMPLETED);
        analysis.setCompletedAt(completedAt);
        analysis.setDuration(Duration.between(startedAt, completedAt).toMillis());
        analysis.setProgressPercent(100);
        analysisRepository.save(analysis);

        activityLogService.success(
                analysis.getUploadedFile().getUser(),
                ActivityType.ANALYSIS_COMPLETED,
                "Inspection and detection completed with %d packets".formatted(analysis.getTotalPackets()));
    }

    /** Backward-compatible helper that records inspection results and finalises immediately. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID analysisId, InspectionProgress progress, Instant startedAt) {
        saveInspectionResults(analysisId, progress);
        complete(analysisId, startedAt);
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
