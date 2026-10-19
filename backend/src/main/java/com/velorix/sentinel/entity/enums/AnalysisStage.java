package com.velorix.sentinel.entity.enums;

/**
 * Coarse stage of the inspection pipeline, surfaced to the UI stepper.
 *
 * <p>Deliberately independent from {@link AnalysisStatus}: the status answers
 * "is this run alive?" while the stage answers "what is it doing right now?".</p>
 */
public enum AnalysisStage {

    /** Run accepted, capture located and opened. */
    PREPARING,

    /** Frames are being pulled off the capture stream. */
    READING,

    /** Frames are being decoded into protocol metadata. */
    EXTRACTING,

    /** Decoded metadata and aggregates are being flushed to the database. */
    SAVING,

    COMPLETED,

    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
