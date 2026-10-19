package com.velorix.sentinel.detection.rules;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.DetectionRule;
import com.velorix.sentinel.detection.PacketRecord;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.detection.support.AbstractRuleEvaluator;
import com.velorix.sentinel.detection.support.Observation;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Malformed or implausibly sized frames.
 *
 * <p>Oversized frames beyond the Ethernet MTU and frames the decoder rejected are both
 * signs of evasion attempts, fragmentation attacks or a corrupted capture — an analyst
 * needs to know which before trusting any other finding.</p>
 */
@Component
public class AbnormalPacketSizeRule implements DetectionRule {

    /** Ethernet MTU plus headroom for jumbo frames seen on legitimate datacentre links. */
    private static final int OVERSIZE_BYTES = 9_000;
    private static final long MIN_EVENTS = 5;
    private static final double MALFORMED_RATIO_THRESHOLD = 0.05d;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "abnormal-packet-size",
            "Malformed or abnormally sized frames",
            "1.0.0",
            "Frames that failed to decode or exceed plausible link-layer sizes.",
            ThreatType.ABNORMAL_PACKET_SIZE,
            ThreatSeverity.MEDIUM,
            "T1027",
            "Obfuscated Files or Information",
            "Counts frames larger than " + OVERSIZE_BYTES + " bytes and frames the decoder marked "
                    + "malformed, reporting each group once it exceeds " + MIN_EVENTS + " frames or "
                    + (int) (MALFORMED_RATIO_THRESHOLD * 100) + "% of the capture.",
            "Confirm the capture is intact. If it is, treat the frames as a possible evasion or "
                    + "fragmentation attack and inspect the involved hosts.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Observation oversized;
        private final Observation malformed;
        private long total;

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
            this.oversized = new Observation(context.limits().maxSampleFrames());
            this.malformed = new Observation(context.limits().maxSampleFrames());
        }

        @Override
        public void accept(PacketRecord packet) {
            total++;
            if (packet.packetLength() > OVERSIZE_BYTES) {
                oversized.record(packet);
            }
            if (packet.malformed()) {
                malformed.record(packet);
            }
        }

        @Override
        public List<ThreatCandidate> finish() {
            if (oversized.count() >= MIN_EVENTS) {
                emit(
                        ThreatSeverity.MEDIUM,
                        0.7d,
                        "Abnormally large frames observed",
                        oversized.count() + " frames exceeded " + OVERSIZE_BYTES
                                + " bytes, which is above any plausible link-layer size for this capture.",
                        "oversizedFrames=" + oversized.count()
                                + "; thresholdBytes=" + OVERSIZE_BYTES
                                + "; totalFrames=" + total
                                + "; bytes=" + oversized.bytes(),
                        oversized.lastSourceIp(),
                        oversized.lastDestinationIp(),
                        Protocol.OTHER,
                        oversized);
            }
            double malformedRatio = total == 0 ? 0.0d : malformed.count() / (double) total;
            if (malformed.count() >= MIN_EVENTS && malformedRatio >= MALFORMED_RATIO_THRESHOLD) {
                emit(
                        escalate(ThreatSeverity.MEDIUM, malformedRatio >= 0.25d),
                        Math.min(0.55d + malformedRatio, 0.9d),
                        "Malformed frames in capture",
                        malformed.count() + " frames (" + String.format("%.1f%%", malformedRatio * 100)
                                + " of the capture) could not be fully decoded.",
                        "malformedFrames=" + malformed.count()
                                + "; totalFrames=" + total
                                + "; ratio=" + String.format("%.3f", malformedRatio)
                                + "; threshold=" + MALFORMED_RATIO_THRESHOLD,
                        malformed.lastSourceIp(),
                        malformed.lastDestinationIp(),
                        Protocol.OTHER,
                        malformed);
            }
            return super.finish();
        }
    }
}
