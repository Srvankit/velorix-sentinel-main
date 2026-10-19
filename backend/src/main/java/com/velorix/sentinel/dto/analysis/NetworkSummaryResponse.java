package com.velorix.sentinel.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the analysis overview screen needs about the traffic itself, derived entirely
 * from the frames stored for the run - no value is estimated or mocked.
 *
 * @param captureDurationMs span covered by the capture, null when it cannot be derived
 * @param packetsPerSecond  frames divided by capture duration, 0 for instantaneous captures
 * @param bitsPerSecond     wire bandwidth over the capture window
 */
@Schema(name = "NetworkSummaryResponse", description = "Traffic level statistics for one inspection run")
public record NetworkSummaryResponse(
        UUID analysisId,
        long totalPackets,
        long malformedPackets,
        long suspiciousPackets,
        long totalBytes,
        double averagePacketSize,
        long uniqueSourceIps,
        long uniqueDestinationIps,
        long uniqueAddresses,
        long uniquePorts,
        Instant captureStartedAt,
        Instant captureEndedAt,
        Long captureDurationMs,
        Long processingTimeMs,
        double packetsPerSecond,
        double bitsPerSecond,
        List<ProtocolDistributionEntry> protocolDistribution,
        List<TopEntry> topSourceIps,
        List<TopEntry> topDestinationIps,
        List<TopEntry> topTalkers,
        List<TopEntry> topPorts) {
}
