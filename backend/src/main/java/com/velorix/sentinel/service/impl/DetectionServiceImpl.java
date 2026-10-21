package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.detection.DetectionOutcome;
import com.velorix.sentinel.detection.ThreatDetectionEngine;
import com.velorix.sentinel.dto.threat.DetectionRunResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.DetectionService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a detection pass: authorise, scan, then persist the aggregate verdict.
 *
 * <p>Streaming frame inspection runs outside a broad transaction so long rule evaluations
 * do not exhaust the connection pool; findings and summary persistence are committed
 * atomically via {@link AnalysisRunStateService}.</p>
 */
@Service
public class DetectionServiceImpl implements DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionServiceImpl.class);
    private static final String RESOURCE = "Analysis";

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunStateService runState;
    private final ThreatDetectionEngine engine;
    private final CurrentUserProvider currentUserProvider;
    private final Set<UUID> activeDetections = ConcurrentHashMap.newKeySet();

    public DetectionServiceImpl(
            AnalysisRepository analysisRepository,
            AnalysisRunStateService runState,
            ThreatDetectionEngine engine,
            CurrentUserProvider currentUserProvider) {
        this.analysisRepository = analysisRepository;
        this.runState = runState;
        this.engine = engine;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public DetectionRunResponse run(UUID analysisId) {
        Analysis analysis = loadOwned(analysisId);
        if (analysis.getStatus() == AnalysisStatus.PROCESSING || analysis.getStatus() == AnalysisStatus.QUEUED) {
            throw new ResourceConflictException(
                    "Analysis " + analysisId + " is currently being processed; manual detection cannot run concurrently.");
        }
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BadRequestException(
                    "Detection can only run on a completed inspection; this run is "
                            + analysis.getStatus() + ".");
        }
        return runInternal(analysisId);
    }

    @Override
    public DetectionRunResponse runInternal(UUID analysisId) {
        if (!activeDetections.add(analysisId)) {
            throw new ResourceConflictException(
                    "Threat detection is already in progress for analysis " + analysisId);
        }
        try {
            Analysis analysis = analysisRepository.findWithDetailsById(analysisId)
                    .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));

            if (analysis.getStatus() != AnalysisStatus.PROCESSING && analysis.getStatus() != AnalysisStatus.COMPLETED) {
                throw new BadRequestException(
                        "Detection cannot run on an analysis with status " + analysis.getStatus() + ".");
            }
            if (analysis.getTotalPackets() <= 0) {
                throw new BadRequestException("This inspection recorded no packets to analyse.");
            }

            DetectionOutcome outcome = engine.run(
                    analysisId,
                    analysis.getTotalPackets(),
                    analysis.getCaptureStartedAt(),
                    analysis.getCaptureEndedAt());

            runState.saveDetectionOutcome(analysisId, outcome);

            log.info("Detection pass stored for analysis {}: {} findings, risk {}",
                    analysisId, outcome.totalThreats(), outcome.riskScore());

            return new DetectionRunResponse(
                    outcome.analysisId(),
                    outcome.packetsScanned(),
                    outcome.rulesExecuted(),
                    outcome.totalThreats(),
                    outcome.criticalThreats(),
                    outcome.highThreats(),
                    outcome.mediumThreats(),
                    outcome.lowThreats(),
                    outcome.riskScore(),
                    outcome.overallStatus(),
                    outcome.durationMs());
        } finally {
            activeDetections.remove(analysisId);
        }
    }

    private Analysis loadOwned(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        return (ownerScope == null
                ? analysisRepository.findWithDetailsById(analysisId)
                : analysisRepository.findByIdAndOwner(analysisId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));
    }
}

