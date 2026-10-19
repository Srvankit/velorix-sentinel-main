package com.velorix.sentinel.dto.threat;

import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatStatus;
import com.velorix.sentinel.entity.enums.ThreatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A finding raised by the detection engine, with the provenance an analyst needs to
 * either act on it or dismiss it.
 */
@Schema(name = "ThreatResponse", description = "A finding raised by the detection engine")
public record ThreatResponse(
        UUID id,
        UUID analysisId,
        ThreatType threatType,
        ThreatSeverity severity,
        ThreatStatus status,

        @Schema(description = "Detector confidence between 0 and 1") double confidenceScore,
        @Schema(description = "Detector confidence as a whole percentage") int confidencePercent,

        String title,
        String description,
        String recommendation,
        String evidence,

        @Schema(description = "Identifier of the rule that produced the finding") String detectionRule,
        String ruleVersion,
        String mitreTechnique,
        String mitreTechniqueName,

        String sourceIp,
        String destinationIp,
        Protocol protocol,

        long packetCount,
        @Schema(description = "Sample of contributing packet numbers") List<Long> samplePacketNumbers,

        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant detectedAt,
        Instant createdAt) {
}
