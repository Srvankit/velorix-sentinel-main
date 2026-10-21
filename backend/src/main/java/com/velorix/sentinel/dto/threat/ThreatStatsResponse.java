package com.velorix.sentinel.dto.threat;

import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated view of the threat feed, used to drive the dashboard visualisations without
 * making the client page through every finding.
 */
@Schema(name = "ThreatStatsResponse", description = "Aggregated threat statistics")
public record ThreatStatsResponse(
        long totalThreats,
        @Schema(description = "Risk score (0-100), null while detection has not executed")
        Integer riskScore,
        @Schema(description = "Overall status classification, null while detection has not executed")
        OverallStatus overallStatus,
        Map<ThreatSeverity, Long> severityCounts,
        Map<ThreatType, Long> categoryCounts,
        List<HostCount> topSources,
        List<HostCount> topDestinations,
        List<TimelineBucket> timeline) {

    @Schema(name = "ThreatHostCount", description = "Finding count for one address")
    public record HostCount(String ip, long count) {
    }

    @Schema(name = "ThreatTimelineBucket", description = "Findings within one time bucket")
    public record TimelineBucket(Instant bucketStart, long total, long critical, long high,
                                 long medium, long low) {
    }
}
