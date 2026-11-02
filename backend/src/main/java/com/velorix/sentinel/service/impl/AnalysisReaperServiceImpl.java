package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.service.AnalysisReaperService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Recovers stranded inspection runs after server crashes and reaps long-running
 * runs that exceed the configured inspection timeout.
 *
 * <p>Ensures that {@code idx_analyses_active_per_file} and {@code existsActiveForFile}
 * never permanently lock captures (HTTP 409 Conflict) following ungraceful restarts
 * or worker stalls.</p>
 */
@Service
public class AnalysisReaperServiceImpl implements AnalysisReaperService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisReaperServiceImpl.class);

    public static final String SERVER_RESTARTED_REASON = "Server restarted during processing; please re-run.";

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunStateService runState;
    private final InspectionProperties properties;

    public AnalysisReaperServiceImpl(
            AnalysisRepository analysisRepository,
            AnalysisRunStateService runState,
            InspectionProperties properties) {
        this.analysisRepository = analysisRepository;
        this.runState = runState;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Override
    public int recoverStuckAnalysesOnStartup() {
        log.info("Checking for stranded analyses from previous application instances...");
        List<Analysis> stranded = analysisRepository.findByStatusIn(
                List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING));

        if (stranded.isEmpty()) {
            log.info("No stranded analyses found on startup");
            return 0;
        }

        log.warn("Found {} stranded analysis run(s) from previous application instance; marking as FAILED", stranded.size());
        int recovered = 0;
        for (Analysis analysis : stranded) {
            try {
                runState.fail(analysis.getId(), SERVER_RESTARTED_REASON, analysis.getStartedAt());
                recovered++;
            } catch (Exception ex) {
                log.error("Failed to recover stranded analysis {}: {}", analysis.getId(), ex.getMessage(), ex);
            }
        }
        log.info("Recovered {} stranded analysis run(s)", recovered);
        return recovered;
    }

    @Scheduled(
            fixedDelayString = "${velorix.inspection.reaper-interval:60000}",
            initialDelayString = "${velorix.inspection.reaper-initial-delay:30000}")
    @Override
    public int reapTimedOutAnalyses() {
        Instant cutoff = Instant.now().minus(properties.maxDuration());
        List<Analysis> timedOut = analysisRepository.findStuckInStatusBefore(AnalysisStatus.PROCESSING, cutoff);

        if (timedOut.isEmpty()) {
            return 0;
        }

        long maxMinutes = properties.maxDuration().toMinutes();
        String reason = "Inspection timed out after exceeding %d minutes; please re-run.".formatted(maxMinutes);
        log.warn("Reaping {} timed-out analysis run(s) exceeding max duration of {} minutes", timedOut.size(), maxMinutes);

        int reaped = 0;
        for (Analysis analysis : timedOut) {
            try {
                runState.fail(analysis.getId(), reason, analysis.getStartedAt());
                reaped++;
            } catch (Exception ex) {
                log.error("Failed to reap timed-out analysis {}: {}", analysis.getId(), ex.getMessage(), ex);
            }
        }
        log.info("Reaped {} timed-out analysis run(s)", reaped);
        return reaped;
    }
}
