package com.velorix.sentinel.service;

import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import java.util.UUID;

/**
 * Entry point of the deep packet inspection pipeline.
 *
 * <p>Starting a run is asynchronous by contract: the call returns as soon as the run is
 * accepted and persisted, and the caller polls the analysis for progress. Captures take
 * minutes to inspect, so holding an HTTP connection open for the duration is not an
 * option.</p>
 */
public interface InspectionService {

    /**
     * Accepts a capture for inspection and schedules the pipeline.
     *
     * @param uploadedFileId capture owned by the caller
     * @return the freshly created run, in {@code QUEUED} state
     */
    AnalysisResponse start(UUID uploadedFileId);
}
