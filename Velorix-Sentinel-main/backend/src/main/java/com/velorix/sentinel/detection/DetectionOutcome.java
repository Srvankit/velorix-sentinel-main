package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.OverallStatus;
import java.util.UUID;

/**
 * Result of one detection pass.
 *
 * @param analysisId     run that was scanned
 * @param packetsScanned frames streamed through the rules
 * @param rulesExecuted  rules that were enabled for this pass
 * @param totalThreats   findings persisted
 * @param riskScore      aggregate score, 0-100
 * @param durationMs     wall clock time of the pass
 */
public record DetectionOutcome(
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
