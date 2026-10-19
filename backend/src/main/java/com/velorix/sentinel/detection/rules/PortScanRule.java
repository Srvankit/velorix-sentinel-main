package com.velorix.sentinel.detection.rules;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.DetectionRule;
import com.velorix.sentinel.detection.PacketRecord;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.support.AbstractRuleEvaluator;
import com.velorix.sentinel.detection.support.Observation;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Horizontal / vertical port scan: one source touching many distinct ports on one target.
 *
 * <p>Counting distinct ports rather than packets is what separates a scan from a chatty
 * client: a browser opens dozens of connections to port 443, a scanner opens one
 * connection each to hundreds of different ports.</p>
 */
@Component
public class PortScanRule implements DetectionRule {

    /** Distinct ports on one target before the behaviour stops looking like normal use. */
    private static final int PORT_THRESHOLD = 15;
    /** Above this the intent is unambiguous and the finding escalates. */
    private static final int AGGRESSIVE_PORT_THRESHOLD = 100;
    /** Distinct ports retained per pair; beyond it we only keep counting. */
    private static final int MAX_PORTS_TRACKED = 4_096;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "port-scan",
            "Port scan",
            "1.0.0",
            "A single host probing an unusual number of distinct ports on one target.",
            ThreatType.PORT_SCAN,
            ThreatSeverity.MEDIUM,
            "T1046",
            "Network Service Discovery",
            "Counts distinct destination ports per source/target pair across the capture and "
                    + "raises a finding once the count exceeds " + PORT_THRESHOLD + ".",
            "Confirm whether the source is an approved scanner. If not, block it at the perimeter, "
                    + "review what the probed services expose and ensure unused ports are closed.");

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
        private final Map<String, Set<Integer>> ports = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (!packet.hasPorts() || packet.protocol() == Protocol.ARP) {
                return;
            }
            String key = packet.sourceIp() + '>' + packet.destinationIp();
            Observation observation = track(traffic, key);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            Set<Integer> seen = ports.computeIfAbsent(key, ignored -> new HashSet<>());
            if (seen.size() < MAX_PORTS_TRACKED) {
                seen.add(packet.destinationPort());
            }
        }

        @Override
        public java.util.List<com.velorix.sentinel.detection.ThreatCandidate> finish() {
            traffic.forEach((key, observation) -> {
                Set<Integer> seen = ports.get(key);
                int distinctPorts = seen == null ? 0 : seen.size();
                if (distinctPorts < PORT_THRESHOLD || !canEmit()) {
                    return;
                }
                boolean aggressive = distinctPorts >= AGGRESSIVE_PORT_THRESHOLD;
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), aggressive),
                        Math.min(0.55d + (distinctPorts / 400.0d), 0.97d),
                        "Port scan from " + source,
                        source + " probed " + distinctPorts + " distinct ports on " + target
                                + " using " + observation.count() + " packets.",
                        "distinctPorts=" + distinctPorts
                                + "; threshold=" + PORT_THRESHOLD
                                + "; packets=" + observation.count()
                                + "; windowSeconds=" + String.format("%.1f", observation.spanSeconds())
                                + "; portsPerSecond=" + String.format("%.2f", distinctPorts / observation.spanSeconds()),
                        source,
                        target,
                        Protocol.TCP,
                        observation);
            });
            return super.finish();
        }
    }
}
