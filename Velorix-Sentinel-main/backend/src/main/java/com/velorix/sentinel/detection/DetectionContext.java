package com.velorix.sentinel.detection;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything a rule may know about the run it is inspecting.
 *
 * @param analysisId   run the findings belong to
 * @param totalPackets frames the run decoded, used to normalise rate based heuristics
 * @param captureStart first frame timestamp, null when the run recorded none
 * @param captureEnd   last frame timestamp, null when the run recorded none
 * @param limits       shared ceilings that keep a rule's state bounded
 */
public record DetectionContext(
        UUID analysisId,
        long totalPackets,
        Instant captureStart,
        Instant captureEnd,
        DetectionLimits limits) {

    /** Capture span in milliseconds, or 0 when it cannot be derived. */
    public long captureDurationMs() {
        if (captureStart == null || captureEnd == null) {
            return 0L;
        }
        long millis = captureEnd.toEpochMilli() - captureStart.toEpochMilli();
        return Math.max(millis, 0L);
    }
}
