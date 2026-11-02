package com.velorix.sentinel.service;

/**
 * Reaps stranded, crashed, or timed-out inspection runs so that capture files
 * are never permanently locked in {@code QUEUED} or {@code PROCESSING} state.
 */
public interface AnalysisReaperService {

    /**
     * Recovers analyses left in {@code QUEUED} or {@code PROCESSING} state after an
     * ungraceful shutdown or container restart.
     *
     * @return count of recovered analyses marked as {@code FAILED}
     */
    int recoverStuckAnalysesOnStartup();

    /**
     * Reaps analyses in {@code PROCESSING} that exceeded the maximum configured duration.
     *
     * @return count of timed-out analyses marked as {@code FAILED}
     */
    int reapTimedOutAnalyses();
}
