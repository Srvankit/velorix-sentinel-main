package com.velorix.sentinel.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Every series the dashboard visualises, in one round trip.
 *
 * <p>Series are always present; when nothing has been analysed yet they are empty lists,
 * which the client renders as an empty state instead of inventing data.</p>
 */
@Schema(name = "DashboardChartsResponse", description = "Aggregated chart series for the dashboard")
public record DashboardChartsResponse(
        List<TrendPoint> threatTrend,
        List<NamedCount> protocolDistribution,
        List<NamedCount> severityBreakdown,
        List<VolumePoint> packetVolume,
        List<NamedCount> topSourceIps,
        List<NamedCount> topDestinationIps) {

    @Schema(name = "DashboardTrendPoint", description = "Findings detected within one day")
    public record TrendPoint(Instant bucketStart, long total, long critical, long high, long medium, long low) {
    }

    @Schema(name = "DashboardNamedCount", description = "A label and its count")
    public record NamedCount(String label, long count) {
    }

    @Schema(name = "DashboardVolumePoint", description = "Packets inspected within one day")
    public record VolumePoint(Instant bucketStart, long packets) {
    }

    /** An account with no data yet: all series empty, never fabricated. */
    public static DashboardChartsResponse empty() {
        return new DashboardChartsResponse(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
