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
 * Excessive repeated connection attempts to the exact same service endpoint.
 *
 * <p>Distinct from a port scan: the target here is one port, hit over and over. That is
 * the shape of a retry storm, a misconfigured client or the transport layer of a
 * credential attack.</p>
 */
@Component
public class RepeatedConnectionRule implements DetectionRule {

    private static final long ATTEMPT_THRESHOLD = 100;
    private static final long SEVERE_ATTEMPT_THRESHOLD = 1_000;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "repeated-connection",
            "Repeated connection attempts",
            "1.0.0",
            "The same source repeatedly opening connections to one service endpoint.",
            ThreatType.REPEATED_CONNECTION,
            ThreatSeverity.MEDIUM,
            "T1499",
            "Endpoint Denial of Service",
            "Counts TCP connection attempts per source, destination and port triple, reporting "
                    + "triples above " + ATTEMPT_THRESHOLD + " attempts.",
            "Identify whether the client is faulty or hostile. Apply connection rate limits and "
                    + "review the service logs for the same window.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> attempts = new HashMap<>();
        private final Map<String, Integer> portByKey = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (!packet.isSynOnly() || !packet.hasPorts()) {
                return;
            }
            String key = packet.sourceIp() + '>' + packet.destinationIp() + ':' + packet.destinationPort();
            Observation observation = track(attempts, key);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            portByKey.putIfAbsent(key, packet.destinationPort());
        }

        @Override
        public List<ThreatCandidate> finish() {
            attempts.forEach((key, observation) -> {
                if (observation.count() < ATTEMPT_THRESHOLD || !canEmit()) {
                    return;
                }
                Integer port = portByKey.get(key);
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), observation.count() >= SEVERE_ATTEMPT_THRESHOLD),
                        Math.min(0.5d + (observation.count() / 4_000.0d), 0.9d),
                        "Repeated connections to " + target + ":" + port,
                        source + " attempted " + observation.count() + " connections to " + target
                                + " on port " + port + ".",
                        "connectionAttempts=" + observation.count()
                                + "; threshold=" + ATTEMPT_THRESHOLD
                                + "; destinationPort=" + port
                                + "; attemptsPerSecond=" + String.format("%.2f", observation.rate())
                                + "; windowSeconds=" + String.format("%.1f", observation.spanSeconds()),
                        source,
                        target,
                        Protocol.TCP,
                        observation);
            });
            return super.finish();
        }
    }
}
