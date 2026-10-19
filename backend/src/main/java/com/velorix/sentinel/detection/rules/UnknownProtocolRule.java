package com.velorix.sentinel.detection.rules;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.DetectionRule;
import com.velorix.sentinel.detection.PacketRecord;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.detection.support.AbstractRuleEvaluator;
import com.velorix.sentinel.detection.support.Observation;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A capture dominated by frames the decoder could not classify.
 *
 * <p>A handful of unknown frames is normal. A capture where they are the majority means
 * either a tunnelled/obfuscated protocol or a decoder blind spot — both are worth an
 * analyst's attention, which is why this is reported as informational-to-medium rather
 * than as an attack.</p>
 */
@Component
public class UnknownProtocolRule implements DetectionRule {

    private static final long MIN_PACKETS = 100;
    private static final double RATIO_THRESHOLD = 0.30d;
    private static final double SEVERE_RATIO = 0.70d;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "unknown-protocol",
            "Unclassified protocol traffic",
            "1.0.0",
            "A large share of the capture could not be classified into a known protocol.",
            ThreatType.UNKNOWN_PROTOCOL,
            ThreatSeverity.LOW,
            "T1095",
            "Non-Application Layer Protocol",
            "Compares frames decoded as an unknown transport or network protocol against the "
                    + "total, reporting captures where they exceed "
                    + (int) (RATIO_THRESHOLD * 100) + "%.",
            "Review a sample of the unclassified frames. Persistent unknown traffic between the "
                    + "same hosts may indicate a custom tunnel and should be investigated.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Observation unknown = new Observation(20);
        private long total;

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            total++;
            if (packet.protocol() == Protocol.OTHER || packet.networkProtocol() == NetworkProtocol.OTHER) {
                unknown.record(packet);
            }
        }

        @Override
        public List<ThreatCandidate> finish() {
            if (total < MIN_PACKETS || unknown.count() == 0) {
                return super.finish();
            }
            double ratio = unknown.count() / (double) total;
            if (ratio < RATIO_THRESHOLD) {
                return super.finish();
            }
            emit(
                    escalate(METADATA.baseSeverity(), ratio >= SEVERE_RATIO),
                    Math.min(0.4d + ratio * 0.4d, 0.85d),
                    "Unclassified traffic dominates the capture",
                    String.format("%.1f%%", ratio * 100) + " of the capture (" + unknown.count()
                            + " of " + total + " frames) could not be attributed to a known protocol.",
                    "unknownFrames=" + unknown.count()
                            + "; totalFrames=" + total
                            + "; ratio=" + String.format("%.3f", ratio)
                            + "; threshold=" + RATIO_THRESHOLD,
                    unknown.lastSourceIp(),
                    unknown.lastDestinationIp(),
                    Protocol.OTHER,
                    unknown);
            return super.finish();
        }
    }
}
