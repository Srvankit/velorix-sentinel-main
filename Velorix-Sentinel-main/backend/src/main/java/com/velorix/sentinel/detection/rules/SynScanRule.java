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
 * Half-open (SYN) scan: many connection attempts that are never completed.
 *
 * <p>The signal is the ratio, not the volume. A busy web client also sends thousands of
 * SYNs, but it acknowledges nearly all of them; a stealth scanner deliberately abandons
 * the handshake, so its SYN-to-ACK ratio collapses.</p>
 */
@Component
public class SynScanRule implements DetectionRule {

    private static final int MIN_SYNS = 20;
    private static final double MAX_COMPLETION_RATIO = 0.25d;
    private static final int AGGRESSIVE_SYNS = 200;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "syn-scan",
            "SYN (half-open) scan",
            "1.0.0",
            "A source sending many TCP SYNs that are never completed into full handshakes.",
            ThreatType.SYN_SCAN,
            ThreatSeverity.HIGH,
            "T1046",
            "Network Service Discovery",
            "Tracks SYN-without-ACK packets per source and compares them against the ACKs that "
                    + "source received. A completion ratio below "
                    + (int) (MAX_COMPLETION_RATIO * 100) + "% over at least " + MIN_SYNS
                    + " attempts is reported.",
            "Rate-limit or block the source at the firewall, enable SYN cookies on exposed hosts "
                    + "and verify no service accepted a connection from it.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> syns = new HashMap<>();
        private final Map<String, long[]> responses = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (packet.protocol() != Protocol.TCP && packet.protocol() != Protocol.HTTP
                    && packet.protocol() != Protocol.HTTPS && packet.protocol() != Protocol.TLS) {
                return;
            }
            if (packet.isSynOnly()) {
                Observation observation = track(syns, packet.sourceIp());
                if (observation != null) {
                    observation.record(packet);
                }
                return;
            }
            // Traffic returning to the scanner tells us whether handshakes completed.
            if (packet.hasFlag("ACK")) {
                counters(packet.destinationIp())[0]++;
            }
            if (packet.isResetOrFin()) {
                counters(packet.destinationIp())[1]++;
            }
        }

        private long[] counters(String host) {
            return responses.computeIfAbsent(host, ignored -> new long[2]);
        }

        @Override
        public List<ThreatCandidate> finish() {
            syns.forEach((source, observation) -> {
                long attempts = observation.count();
                if (attempts < MIN_SYNS || !canEmit()) {
                    return;
                }
                long[] counters = responses.getOrDefault(source, new long[2]);
                double completionRatio = counters[0] / (double) attempts;
                if (completionRatio > MAX_COMPLETION_RATIO) {
                    return;
                }
                emit(
                        escalate(METADATA.baseSeverity(), attempts >= AGGRESSIVE_SYNS),
                        Math.min(0.6d + (1.0d - completionRatio) * 0.35d, 0.96d),
                        "Half-open TCP scan from " + source,
                        source + " opened " + attempts + " TCP connections and completed only "
                                + counters[0] + " of them, the signature of a stealth SYN scan.",
                        "synOnlyPackets=" + attempts
                                + "; acksReceived=" + counters[0]
                                + "; resetsOrFins=" + counters[1]
                                + "; completionRatio=" + String.format("%.3f", completionRatio)
                                + "; maxCompletionRatio=" + MAX_COMPLETION_RATIO
                                + "; synRatePerSecond=" + String.format("%.2f", observation.rate()),
                        source,
                        observation.lastDestinationIp(),
                        Protocol.TCP,
                        observation);
            });
            return super.finish();
        }
    }
}
