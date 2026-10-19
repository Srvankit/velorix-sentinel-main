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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * UDP sweep: one source spraying datagrams across many distinct UDP ports.
 *
 * <p>DNS and DHCP are excluded because they are connectionless by nature and would
 * otherwise dominate the count on any normal capture.</p>
 */
@Component
public class UdpScanRule implements DetectionRule {

    private static final int PORT_THRESHOLD = 20;
    private static final int AGGRESSIVE_PORT_THRESHOLD = 150;
    private static final int MAX_PORTS_TRACKED = 4_096;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "udp-scan",
            "UDP port sweep",
            "1.0.0",
            "A source sending datagrams to an unusual number of distinct UDP ports.",
            ThreatType.UDP_SCAN,
            ThreatSeverity.MEDIUM,
            "T1046",
            "Network Service Discovery",
            "Counts distinct UDP destination ports per source/target pair, ignoring DNS and DHCP, "
                    + "and reports counts above " + PORT_THRESHOLD + ".",
            "Block the source, and confirm that exposed UDP services (SNMP, NTP, TFTP) are either "
                    + "firewalled or hardened against reflection abuse.");

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
            if (packet.protocol() != Protocol.UDP || !packet.hasPorts()) {
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
        public List<ThreatCandidate> finish() {
            traffic.forEach((key, observation) -> {
                Set<Integer> seen = ports.get(key);
                int distinctPorts = seen == null ? 0 : seen.size();
                if (distinctPorts < PORT_THRESHOLD || !canEmit()) {
                    return;
                }
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), distinctPorts >= AGGRESSIVE_PORT_THRESHOLD),
                        Math.min(0.5d + (distinctPorts / 500.0d), 0.93d),
                        "UDP port sweep from " + source,
                        source + " sent datagrams to " + distinctPorts + " distinct UDP ports on "
                                + target + ".",
                        "distinctUdpPorts=" + distinctPorts
                                + "; threshold=" + PORT_THRESHOLD
                                + "; datagrams=" + observation.count()
                                + "; windowSeconds=" + String.format("%.1f", observation.spanSeconds()),
                        source,
                        target,
                        Protocol.UDP,
                        observation);
            });
            return super.finish();
        }
    }
}
