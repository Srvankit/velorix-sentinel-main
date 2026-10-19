package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.OverallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "AnalysisSummaryResponse", description = "Pre-aggregated verdict for an analysis")
public record AnalysisSummaryResponse(
        UUID id,
        UUID analysisId,
        long totalThreats,
        long criticalThreats,
        long highThreats,
        long mediumThreats,
        long lowThreats,
        int riskScore,
        OverallStatus overallStatus) {
}
