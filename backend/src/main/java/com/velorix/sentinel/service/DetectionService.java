package com.velorix.sentinel.service;

import com.velorix.sentinel.dto.threat.DetectionRunResponse;
import java.util.UUID;

/**
 * Runs the detection engine over a completed inspection run.
 */
public interface DetectionService {

    /**
     * Scans one analysis and replaces its findings and summary.
     *
     * @throws com.velorix.sentinel.exception.ResourceNotFoundException when the run does not
     *         exist or is not visible to the caller
     * @throws com.velorix.sentinel.exception.BadRequestException when the run has no packets
     *         to scan yet
     */
    DetectionRunResponse run(UUID analysisId);

    /**
     * Internal background invocation that runs detection over an analysis without requiring
     * an HTTP SecurityContext. Access is strictly scoped to the supplied analysisId.
     *
     * @param analysisId the analysis to scan
     * @return the outcome of the detection pass
     */
    DetectionRunResponse runInternal(UUID analysisId);
}

