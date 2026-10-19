package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.time.Instant;
import java.util.List;

/**
 * A finding produced by a rule, before it is persisted.
 *
 * <p>Rules emit candidates rather than entities so that scoring, deduplication and
 * batching stay the engine's concern and a rule stays a pure function of the frames it
 * saw.</p>
 */
public record ThreatCandidate(
        RuleMetadata rule,
        ThreatType threatType,
        ThreatSeverity severity,
        double confidence,
        String title,
        String description,
        String evidence,
        String recommendation,
        String sourceIp,
        String destinationIp,
        Protocol protocol,
        long packetCount,
        List<Long> samplePacketNumbers,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public ThreatCandidate {
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        samplePacketNumbers = samplePacketNumbers == null ? List.of() : List.copyOf(samplePacketNumbers);
        protocol = protocol == null ? Protocol.OTHER : protocol;
    }

    /** Timestamp used as the finding's {@code detectedAt}. */
    public Instant detectedAt() {
        return lastSeenAt != null ? lastSeenAt : firstSeenAt;
    }
}
