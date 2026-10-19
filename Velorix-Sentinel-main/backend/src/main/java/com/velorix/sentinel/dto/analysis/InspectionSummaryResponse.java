package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated view of one inspection run, served by
 * {@code GET /api/v1/analysis/{id}/summary}.
 *
 * <p>Counters that are cheap to recompute (the protocol histogram) are read live from an
 * indexed GROUP BY; counters that are expensive (distinct address cardinality) are
 * persisted once when the run completes and read back from the analysis row.</p>
 *
 * @param captureDurationMs span covered by the capture itself, null when it cannot be derived
 * @param processingTimeMs  wall clock time the engine spent inspecting
 * @param threatSummary     threat verdict, null until the detection module produces one
 */
@Schema(name = "InspectionSummaryResponse", description = "Aggregated statistics for one inspection run")
public record InspectionSummaryResponse(
        UUID analysisId,
        AnalysisStatus status,
        AnalysisStage stage,
        int progressPercent,

        long totalPackets,
        long malformedPackets,
        boolean truncated,

        List<ProtocolDistributionEntry> protocolDistribution,
        double averagePacketSize,
        long uniqueSourceIps,
        long uniqueDestinationIps,

        Instant captureStartedAt,
        Instant captureEndedAt,
        Long captureDurationMs,
        Long processingTimeMs,

        AnalysisSummaryResponse threatSummary) {
}
