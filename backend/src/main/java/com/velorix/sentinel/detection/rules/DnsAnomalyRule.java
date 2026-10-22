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
 * DNS anomaly: query volume or payload sizes that do not fit ordinary name resolution.
 *
 * <p>Oversized DNS payloads are the classic tell of tunnelling — encoding data into
 * subdomain labels inflates a protocol whose normal messages are tiny.</p>
 */
@Component
public class DnsAnomalyRule implements DetectionRule {

    private static final long QUERY_THRESHOLD = 500;
    private static final double QUERY_RATE_THRESHOLD = 20.0d;
    /** Standard modern EDNS0 buffer threshold (RFC 6891 / DNS Flag Day); excess suggests tunnelling. */
    private static final int DEFAULT_LARGE_DNS_PAYLOAD = 1232;
    private static final double LARGE_PAYLOAD_RATIO = 0.2d;

    private final int largeDnsPayload;

    public DnsAnomalyRule() {
        this(DEFAULT_LARGE_DNS_PAYLOAD);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public DnsAnomalyRule(com.velorix.sentinel.config.properties.DetectionProperties properties) {
        this(properties != null && properties.dnsLargePayloadBytes() != null
                ? properties.dnsLargePayloadBytes()
                : DEFAULT_LARGE_DNS_PAYLOAD);
    }

    public DnsAnomalyRule(int largeDnsPayload) {
        this.largeDnsPayload = largeDnsPayload <= 0 ? DEFAULT_LARGE_DNS_PAYLOAD : largeDnsPayload;
    }

    private static final RuleMetadata METADATA = new RuleMetadata(
            "dns-anomaly",
            "DNS anomaly",
            "1.0.0",
            "Abnormal DNS query volume or oversized DNS payloads suggesting tunnelling.",
            ThreatType.DNS_ANOMALY,
            ThreatSeverity.MEDIUM,
            "T1071.004",
            "Application Layer Protocol: DNS",
            "Groups DNS traffic per source and reports sources exceeding " + QUERY_THRESHOLD
                    + " queries, a rate above " + (int) QUERY_RATE_THRESHOLD
                    + " queries per second, or more than " + (int) (LARGE_PAYLOAD_RATIO * 100)
                    + "% of messages larger than " + DEFAULT_LARGE_DNS_PAYLOAD + " bytes.",
            "Inspect the queried domains, restrict clients to approved resolvers and block the "
                    + "domain if the pattern indicates DNS tunnelling.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context, largeDnsPayload);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> queries = new HashMap<>();
        private final Map<String, long[]> largePayloads = new HashMap<>();
        private final int largePayloadThreshold;

        private Evaluator(DetectionContext context, int largePayloadThreshold) {
            super(METADATA, context);
            this.largePayloadThreshold = largePayloadThreshold;
        }

        @Override
        public void accept(PacketRecord packet) {
            if (packet.sourceIp() == null || "-".equals(packet.sourceIp()) || packet.sourceIp().isBlank()) {
                return;
            }
            boolean dnsPort = packet.hasPorts()
                    && (packet.destinationPort() == 53 || packet.sourcePort() == 53);
            if (packet.protocol() != Protocol.DNS && !dnsPort) {
                return;
            }
            Observation observation = track(queries, packet.sourceIp());
            if (observation == null) {
                return;
            }
            observation.record(packet);
            if (packet.payloadSize() > largePayloadThreshold) {
                largePayloads.computeIfAbsent(packet.sourceIp(), ignored -> new long[1])[0]++;
            }
        }

        @Override
        public List<ThreatCandidate> finish() {
            queries.forEach((source, observation) -> {
                if (!canEmit()) {
                    return;
                }
                long total = observation.count();
                long large = largePayloads.getOrDefault(source, new long[1])[0];
                double largeRatio = total == 0 ? 0.0d : large / (double) total;
                double rate = observation.rate();
                boolean highVolume = total >= QUERY_THRESHOLD && rate >= QUERY_RATE_THRESHOLD;
                boolean tunnelling = total >= 50 && largeRatio >= LARGE_PAYLOAD_RATIO;
                if (!highVolume && !tunnelling) {
                    return;
                }
                emit(
                        escalate(METADATA.baseSeverity(), tunnelling),
                        Math.min(0.5d + (tunnelling ? 0.3d : 0.15d), 0.92d),
                        tunnelling
                                ? "Possible DNS tunnelling from " + source
                                : "Excessive DNS activity from " + source,
                        source + " generated " + total + " DNS messages at "
                                + String.format("%.1f", rate) + " per second, "
                                + large + " of them larger than " + largePayloadThreshold + " bytes.",
                        "dnsMessages=" + total
                                + "; messagesPerSecond=" + String.format("%.2f", rate)
                                + "; oversizedMessages=" + large
                                + "; oversizedRatio=" + String.format("%.3f", largeRatio)
                                + "; oversizedThresholdBytes=" + largePayloadThreshold,
                        source,
                        observation.lastDestinationIp(),
                        Protocol.DNS,
                        observation);
            });
            return super.finish();
        }
    }
}
