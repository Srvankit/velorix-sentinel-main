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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ICMP flood: sustained echo traffic aimed at one host.
 *
 * <p>Rate matters more than total volume here — a long capture legitimately contains many
 * pings, so the rule reports on packets per second, not on the raw count alone.</p>
 */
@Component
public class IcmpFloodRule implements DetectionRule {

    private static final long MIN_PACKETS = 100;
    private static final double RATE_THRESHOLD_PER_SECOND = 50.0d;
    private static final double SEVERE_RATE_PER_SECOND = 500.0d;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "icmp-flood",
            "ICMP flood",
            "1.0.0",
            "Sustained high-rate ICMP traffic directed at a single host.",
            ThreatType.ICMP_FLOOD,
            ThreatSeverity.HIGH,
            "T1498",
            "Network Denial of Service",
            "Groups ICMP frames by source/target pair and reports pairs exceeding "
                    + (int) RATE_THRESHOLD_PER_SECOND + " packets per second over at least "
                    + MIN_PACKETS + " packets.",
            "Rate-limit ICMP at the edge, verify the target host stayed responsive, and block the "
                    + "source if the traffic was not part of authorised monitoring.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> traffic = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (packet.protocol() != Protocol.ICMP && packet.protocol() != Protocol.ICMPV6) {
                return;
            }
            Observation observation = track(traffic, packet.sourceIp() + '>' + packet.destinationIp());
            if (observation != null) {
                observation.record(packet);
            }
        }

        @Override
        public List<ThreatCandidate> finish() {
            traffic.forEach((key, observation) -> {
                double rate = observation.rate();
                if (observation.count() < MIN_PACKETS || rate < RATE_THRESHOLD_PER_SECOND || !canEmit()) {
                    return;
                }
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), rate >= SEVERE_RATE_PER_SECOND),
                        Math.min(0.6d + (rate / (SEVERE_RATE_PER_SECOND * 3)), 0.95d),
                        "ICMP flood targeting " + target,
                        source + " sent " + observation.count() + " ICMP packets to " + target
                                + " at " + String.format("%.1f", rate) + " packets per second.",
                        "icmpPackets=" + observation.count()
                                + "; packetsPerSecond=" + String.format("%.2f", rate)
                                + "; threshold=" + RATE_THRESHOLD_PER_SECOND
                                + "; bytes=" + observation.bytes()
                                + "; windowSeconds=" + String.format("%.1f", observation.spanSeconds()),
                        source,
                        target,
                        Protocol.ICMP,
                        observation);
            });
            return super.finish();
        }
    }
}
