package com.velorix.sentinel.detection.rules;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.DetectionRule;
import com.velorix.sentinel.detection.PacketRecord;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.detection.support.AbstractRuleEvaluator;
import com.velorix.sentinel.detection.support.NetworkAddresses;
import com.velorix.sentinel.detection.support.Observation;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Internal host talking to the internet on a port no ordinary client should be using.
 *
 * <p>Allow-listing the handful of ports normal egress uses is far more robust than trying
 * to enumerate the ports malware prefers.</p>
 */
@Component
public class SuspiciousExternalCommRule implements DetectionRule {

    private static final Set<Integer> EXPECTED_EGRESS_PORTS = Set.of(
            53, 67, 68, 80, 123, 443, 465, 587, 993, 995, 8080, 8443);
    private static final long PACKET_THRESHOLD = 20;
    /** Ports above this range are ephemeral for servers too, so treat them as louder signals. */
    private static final int HIGH_PORT_FLOOR = 1024;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "suspicious-external-comm",
            "Suspicious external communication",
            "1.0.0",
            "An internal host communicating with an external address on an unexpected port.",
            ThreatType.SUSPICIOUS_EXTERNAL_COMM,
            ThreatSeverity.MEDIUM,
            "T1571",
            "Non-Standard Port",
            "Flags internal-to-external conversations on ports outside the expected egress set "
                    + "(web, DNS, mail, NTP) once they exceed " + PACKET_THRESHOLD + " packets.",
            "Verify the destination and the responsible application. Restrict outbound traffic to "
                    + "an approved port allow-list and block the destination if unexplained.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> conversations = new HashMap<>();
        private final Map<String, Integer> portByKey = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (!packet.hasPorts()) {
                return;
            }
            if (!NetworkAddresses.isPrivate(packet.sourceIp())
                    || !NetworkAddresses.isExternal(packet.destinationIp())) {
                return;
            }
            int port = packet.destinationPort();
            if (EXPECTED_EGRESS_PORTS.contains(port)) {
                return;
            }
            String key = packet.sourceIp() + '>' + packet.destinationIp() + ':' + port;
            Observation observation = track(conversations, key);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            portByKey.putIfAbsent(key, port);
        }

        @Override
        public List<ThreatCandidate> finish() {
            conversations.forEach((key, observation) -> {
                if (observation.count() < PACKET_THRESHOLD || !canEmit()) {
                    return;
                }
                Integer port = portByKey.get(key);
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), port != null && port > HIGH_PORT_FLOOR
                                && observation.bytes() > 1_000_000),
                        Math.min(0.45d + (observation.count() / 2_000.0d), 0.85d),
                        "Unexpected egress to " + target + ":" + port,
                        source + " exchanged " + observation.count() + " packets ("
                                + observation.bytes() + " bytes) with the external address "
                                + target + " on port " + port + ", which is outside the expected "
                                + "egress port set.",
                        "destinationPort=" + port
                                + "; packets=" + observation.count()
                                + "; bytes=" + observation.bytes()
                                + "; threshold=" + PACKET_THRESHOLD
                                + "; expectedEgressPorts=" + EXPECTED_EGRESS_PORTS,
                        source,
                        target,
                        Protocol.OTHER,
                        observation);
            });
            return super.finish();
        }
    }
}
