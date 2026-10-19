package com.velorix.sentinel.dto.threat;

import com.velorix.sentinel.entity.enums.OverallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Outcome of a detection pass, returned by {@code POST /api/v1/threats/run/{analysisId}}.
 */
@Schema(name = "DetectionRunResponse", description = "Result of a detection pass over one analysis")
public record DetectionRunResponse(
        UUID analysisId,
        long packetsScanned,
        int rulesExecuted,
        long totalThreats,
        long criticalThreats,
        long highThreats,
        long mediumThreats,
        long lowThreats,
        int riskScore,
        OverallStatus overallStatus,
        long durationMs) {
}
