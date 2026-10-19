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
}
