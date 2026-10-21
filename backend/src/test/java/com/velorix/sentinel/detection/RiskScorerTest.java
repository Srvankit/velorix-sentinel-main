package com.velorix.sentinel.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.OverallStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RiskScorerTest {

    @Test
    @DisplayName("Score 0 with no threats yields SECURE when detection is completed")
    void scoreZeroIsSecure() {
        assertThat(RiskScorer.status(0, 0, 0)).isEqualTo(OverallStatus.SECURE);
        assertThat(RiskScorer.status(0)).isEqualTo(OverallStatus.SECURE);
        assertThat(RiskScorer.status(AnalysisStatus.COMPLETED, true, 0, 0, 0))
                .isEqualTo(OverallStatus.SECURE);
    }

    @Test
    @DisplayName("Boundary scores 25, 50, and 80 produce matching classifications")
    void boundaryThresholdsMatch() {
        // Boundary 25: 24 is LOW_RISK, 25 is ELEVATED
        assertThat(RiskScorer.status(24)).isEqualTo(OverallStatus.LOW_RISK);
        assertThat(RiskScorer.status(25)).isEqualTo(OverallStatus.ELEVATED);

        // Boundary 50: 49 is ELEVATED, 50 is HIGH_RISK
        assertThat(RiskScorer.status(49)).isEqualTo(OverallStatus.ELEVATED);
        assertThat(RiskScorer.status(50)).isEqualTo(OverallStatus.HIGH_RISK);

        // Boundary 80: 79 is HIGH_RISK, 80 is CRITICAL
        assertThat(RiskScorer.status(79)).isEqualTo(OverallStatus.HIGH_RISK);
        assertThat(RiskScorer.status(80)).isEqualTo(OverallStatus.CRITICAL);
    }

    @Test
    @DisplayName("All integer scores from 0 through 100 map to consistent expected classifications")
    void allIntegerScoresConsistent() {
        for (int score = 0; score <= 100; score++) {
            OverallStatus status = RiskScorer.status(score);
            if (score == 0) {
                assertThat(status).isEqualTo(OverallStatus.SECURE);
            } else if (score < 25) {
                assertThat(status).isEqualTo(OverallStatus.LOW_RISK);
            } else if (score < 50) {
                assertThat(status).isEqualTo(OverallStatus.ELEVATED);
            } else if (score < 80) {
                assertThat(status).isEqualTo(OverallStatus.HIGH_RISK);
            } else {
                assertThat(status).isEqualTo(OverallStatus.CRITICAL);
            }
        }
    }

    @Test
    @DisplayName("Critical findings override low risk scores to CRITICAL")
    void criticalThreatOverride() {
        // Even with score 0, a critical finding forces CRITICAL
        assertThat(RiskScorer.status(0, 1, 0)).isEqualTo(OverallStatus.CRITICAL);
        assertThat(RiskScorer.status(10, 1, 0)).isEqualTo(OverallStatus.CRITICAL);
        assertThat(RiskScorer.status(25, 2, 0)).isEqualTo(OverallStatus.CRITICAL);
        assertThat(RiskScorer.status(50, 1, 5)).isEqualTo(OverallStatus.CRITICAL);
    }

    @Test
    @DisplayName("High findings override low risk scores to HIGH_RISK unless critical findings exist")
    void highThreatOverride() {
        assertThat(RiskScorer.status(0, 0, 1)).isEqualTo(OverallStatus.HIGH_RISK);
        assertThat(RiskScorer.status(10, 0, 2)).isEqualTo(OverallStatus.HIGH_RISK);
        assertThat(RiskScorer.status(25, 0, 1)).isEqualTo(OverallStatus.HIGH_RISK);
        // Critical still takes precedence over high
        assertThat(RiskScorer.status(25, 1, 1)).isEqualTo(OverallStatus.CRITICAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "PROCESSING", "FAILED", "UPLOADED"})
    @DisplayName("Non-completed analysis statuses never produce SECURE status")
    void nonCompletedAnalysesNeverSecure(String statusName) {
        AnalysisStatus status = AnalysisStatus.valueOf(statusName);
        OverallStatus result = RiskScorer.status(status, false, 0, 0, 0);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Completed analysis without detection execution never produces SECURE status")
    void completedWithoutDetectionNeverSecure() {
        OverallStatus result = RiskScorer.status(AnalysisStatus.COMPLETED, false, 0, 0, 0);
        assertThat(result).isNull();
    }
}
