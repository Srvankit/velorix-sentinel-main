package com.velorix.sentinel.entity.enums;

/**
 * Lifecycle of an uploaded capture, independent from the analysis lifecycle.
 */
public enum UploadStatus {

    /** Bytes accepted and persisted in storage. */
    UPLOADED,

    /** Integrity and format checks are running. */
    VALIDATING,

    /** The capture passed validation and can be scheduled for inspection. */
    READY_FOR_ANALYSIS,

    /** Ingestion failed; the capture is unusable. */
    FAILED,

    /** Soft deleted by its owner. The binary is gone, the audit row remains. */
    DELETED;

    public boolean isDeleted() {
        return this == DELETED;
    }
}
