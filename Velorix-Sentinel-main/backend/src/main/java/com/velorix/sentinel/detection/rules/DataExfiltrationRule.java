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
import org.springframework.stereotype.Component;

/**
 * Data exfiltration: an internal host pushing an unusual volume of bytes to the internet.
 *
 * <p>Direction is the whole point. Downloading a gigabyte is routine; uploading one to a
 * single external address, from a workstation, is not.</p>
 */
@Component
public class DataExfiltrationRule implements DetectionRule {

    private static final long BYTES_THRESHOLD = 50L * 1024 * 1024;
    private static final long SEVERE_BYTES_THRESHOLD = 500L * 1024 * 1024;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "data-exfiltration",
            "Outbound data exfiltration",
            "1.0.0",
            "An internal host transferring a large volume of data to a single external address.",
            ThreatType.DATA_EXFILTRATION,
            ThreatSeverity.HIGH,
            "T1048",
            "Exfiltration Over Alternative Protocol",
            "Sums outbound payload bytes per internal source to external destination and reports "
                    + "pairs exceeding " + (BYTES_THRESHOLD / (1024 * 1024)) + " MB.",
            "Identify the process and user behind the transfer, verify the destination's reputation, "
                    + "and contain the host if the transfer was not authorised.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> transfers = new HashMap<>();
        private final Map<String, long[]> payloadBytes = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (packet.payloadSize() <= 0) {
                return;
            }
            if (!NetworkAddresses.isPrivate(packet.sourceIp())
                    || !NetworkAddresses.isExternal(packet.destinationIp())) {
                return;
            }
            String key = packet.sourceIp() + '>' + packet.destinationIp();
            Observation observation = track(transfers, key);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            payloadBytes.computeIfAbsent(key, ignored -> new long[1])[0] += packet.payloadSize();
        }

        @Override
        public List<ThreatCandidate> finish() {
            transfers.forEach((key, observation) -> {
                long bytes = payloadBytes.getOrDefault(key, new long[1])[0];
                if (bytes < BYTES_THRESHOLD || !canEmit()) {
                    return;
                }
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                double megabytes = bytes / (1024.0d * 1024.0d);
                emit(
                        escalate(METADATA.baseSeverity(), bytes >= SEVERE_BYTES_THRESHOLD),
                        Math.min(0.6d + (bytes / (double) SEVERE_BYTES_THRESHOLD) * 0.3d, 0.95d),
                        "Large outbound transfer to " + target,
                        source + " uploaded " + String.format("%.1f", megabytes) + " MB to the external "
                                + "address " + target + " across " + observation.count() + " packets.",
                        "outboundPayloadBytes=" + bytes
                                + "; megabytes=" + String.format("%.2f", megabytes)
                                + "; thresholdMb=" + (BYTES_THRESHOLD / (1024 * 1024))
                                + "; packets=" + observation.count()
                                + "; bytesPerSecond=" + String.format("%.0f", bytes / observation.spanSeconds()),
                        source,
                        target,
                        Protocol.OTHER,
                        observation);
            });
            return super.finish();
        }
    }
}
