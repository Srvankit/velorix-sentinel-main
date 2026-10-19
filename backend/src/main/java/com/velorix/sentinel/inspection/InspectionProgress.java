package com.velorix.sentinel.inspection;

import com.velorix.sentinel.entity.enums.AnalysisStage;
import java.time.Instant;

/**
 * Snapshot of one inspection run handed to the persistence layer at checkpoints.
 *
 * @param stage             current pipeline stage
 * @param processedPackets  frames decoded so far
 * @param malformedPackets  frames recorded but not fully decodable
 * @param progressPercent   0-100 completion estimate
 * @param firstFrameAt      timestamp of the first frame, null before any frame is read
 * @param lastFrameAt       timestamp of the most recent frame
 * @param totalWireBytes    summed wire length of every decoded frame
 * @param truncated         true when the engine hit the configured packet ceiling
 */
public record InspectionProgress(
        AnalysisStage stage,
        long processedPackets,
        long malformedPackets,
        int progressPercent,
        Instant firstFrameAt,
        Instant lastFrameAt,
        long totalWireBytes,
        boolean truncated) {

    /** Mean wire length, or zero when nothing was decoded. */
    public double averagePacketSize() {
        return processedPackets == 0 ? 0d : (double) totalWireBytes / processedPackets;
    }

    /** Span covered by the capture in milliseconds, or null when it cannot be derived. */
    public Long captureDurationMillis() {
        if (firstFrameAt == null || lastFrameAt == null || lastFrameAt.isBefore(firstFrameAt)) {
            return null;
        }
        return java.time.Duration.between(firstFrameAt, lastFrameAt).toMillis();
    }
}
