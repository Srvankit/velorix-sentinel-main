package com.velorix.sentinel.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Headline counters shown on the dashboard summary cards.
 *
 * <p>Every value is derived from persisted rows; an empty account legitimately reports
 * zeros rather than sample data.</p>
 */
@Schema(name = "DashboardSummaryResponse", description = "Headline security posture counters")
public record DashboardSummaryResponse(
        @Schema(description = "0-100 posture score, 100 when no risk has been observed") int securityScore,
        long totalAnalyses,
        long completedAnalyses,
        long runningAnalyses,
        long threatsDetected,
        long criticalThreats,
        long highThreats,
        long filesUploaded,
        @Schema(description = "Findings still open or acknowledged") long activeInvestigations,
        long packetsInspected) {
}
