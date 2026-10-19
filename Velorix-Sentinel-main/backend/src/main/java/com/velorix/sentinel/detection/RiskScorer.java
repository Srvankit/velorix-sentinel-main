package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import java.util.Map;

/**
 * Turns a severity histogram into a single 0-100 risk score.
 *
 * <p>The weights are deliberately non-linear: one critical finding must outrank a long
 * tail of low ones, because a single confirmed exfiltration matters more than fifty
 * unclassified frames. The score saturates at 100 rather than growing without bound, so
 * "very bad" and "catastrophic" both read as 100 and the status enum carries the nuance.</p>
 */
public final class RiskScorer {

    private static final Map<ThreatSeverity, Integer> WEIGHTS = Map.of(
            ThreatSeverity.CRITICAL, 30,
            ThreatSeverity.HIGH, 14,
            ThreatSeverity.MEDIUM, 5,
            ThreatSeverity.LOW, 2);

    private RiskScorer() {
    }

    public static int score(long critical, long high, long medium, long low) {
        long raw = critical * WEIGHTS.get(ThreatSeverity.CRITICAL)
                + high * WEIGHTS.get(ThreatSeverity.HIGH)
                + medium * WEIGHTS.get(ThreatSeverity.MEDIUM)
                + low * WEIGHTS.get(ThreatSeverity.LOW);
        return (int) Math.min(raw, 100L);
    }

    public static OverallStatus status(int riskScore, long critical, long high) {
        if (critical > 0 || riskScore >= 80) {
            return OverallStatus.CRITICAL;
        }
        if (high > 0 || riskScore >= 50) {
            return OverallStatus.HIGH_RISK;
        }
        if (riskScore >= 25) {
            return OverallStatus.ELEVATED;
        }
        if (riskScore > 0) {
            return OverallStatus.LOW_RISK;
        }
        return OverallStatus.SECURE;
    }
}
