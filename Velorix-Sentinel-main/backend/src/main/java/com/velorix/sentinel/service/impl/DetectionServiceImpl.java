package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.detection.DetectionOutcome;
import com.velorix.sentinel.detection.ThreatDetectionEngine;
import com.velorix.sentinel.dto.threat.DetectionRunResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.DetectionService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a detection pass: authorise, scan, then persist the aggregate verdict.
 *
 * <p>The whole pass runs in one transaction so a run can never be left with the old
 * summary next to new findings; either both are replaced or neither is.</p>
 */
@Service
public class DetectionServiceImpl implements DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionServiceImpl.class);
    private static final String RESOURCE = "Analysis";

    private final AnalysisRepository analysisRepository;
    private final AnalysisSummaryRepository analysisSummaryRepository;
    private final ThreatDetectionEngine engine;
    private final CurrentUserProvider currentUserProvider;

    public DetectionServiceImpl(
            AnalysisRepository analysisRepository,
            AnalysisSummaryRepository analysisSummaryRepository,
            ThreatDetectionEngine engine,
            CurrentUserProvider currentUserProvider) {
        this.analysisRepository = analysisRepository;
        this.analysisSummaryRepository = analysisSummaryRepository;
        this.engine = engine;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public DetectionRunResponse run(UUID analysisId) {
        Analysis analysis = loadOwned(analysisId);
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BadRequestException(
                    "Detection can only run on a completed inspection; this run is "
                            + analysis.getStatus() + ".");
        }
        if (analysis.getTotalPackets() <= 0) {
            throw new BadRequestException("This inspection recorded no packets to analyse.");
        }

        DetectionOutcome outcome = engine.run(
                analysisId,
                analysis.getTotalPackets(),
                analysis.getCaptureStartedAt(),
                analysis.getCaptureEndedAt());

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
        analysisRepository.save(analysis);

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
    }

    private Analysis loadOwned(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        return (ownerScope == null
                ? analysisRepository.findWithDetailsById(analysisId)
                : analysisRepository.findByIdAndOwner(analysisId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));
    }
}
