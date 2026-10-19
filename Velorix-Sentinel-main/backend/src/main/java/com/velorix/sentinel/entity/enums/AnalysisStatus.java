package com.velorix.sentinel.entity.enums;

/**
 * Lifecycle of an uploaded capture and of the analysis derived from it.
 */
public enum AnalysisStatus {

    /** The capture has been stored but no work has been scheduled yet. */
    UPLOADED,

    /** Queued for the inspection pipeline. */
    QUEUED,

    /** Deep packet inspection is currently running. */
    PROCESSING,

    /** Inspection finished and results are available. */
    COMPLETED,

    /** Inspection aborted; see the failure reason on the analysis. */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
