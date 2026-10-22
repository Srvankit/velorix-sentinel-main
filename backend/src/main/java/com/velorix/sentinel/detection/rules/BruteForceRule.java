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
 * Credential brute force against an authentication service.
 *
 * <p>Packet metadata cannot see a failed password, so the rule reasons about shape: many
 * short-lived connections to an authentication port from one source is what repeated
 * login attempts look like on the wire.</p>
 */
@Component
public class BruteForceRule implements DetectionRule {

    private static final long DEFAULT_ATTEMPT_THRESHOLD = 30;
    private static final long SEVERE_ATTEMPT_THRESHOLD = 200;
    private static final double MIN_ATTEMPT_RATE_PER_SECOND = 0.1d;
    private static final double MAX_BENIGN_SPAN_SECONDS = 120.0d;

    private final long attemptThreshold;

    public BruteForceRule() {
        this(DEFAULT_ATTEMPT_THRESHOLD);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public BruteForceRule(com.velorix.sentinel.config.properties.DetectionProperties properties) {
        this(properties != null && properties.bruteForceAttemptThreshold() != null
                ? properties.bruteForceAttemptThreshold()
                : DEFAULT_ATTEMPT_THRESHOLD);
    }

    public BruteForceRule(long attemptThreshold) {
        this.attemptThreshold = attemptThreshold <= 0 ? DEFAULT_ATTEMPT_THRESHOLD : attemptThreshold;
    }

    /** Ports whose whole purpose is authentication, so repetition is meaningful. */
    private static final Map<Integer, String> AUTH_SERVICES = Map.of(
            21, "FTP",
            22, "SSH",
            23, "Telnet",
            25, "SMTP",
            110, "POP3",
            143, "IMAP",
            389, "LDAP",
            445, "SMB",
            3306, "MySQL",
            3389, "RDP");

    private static final RuleMetadata METADATA = new RuleMetadata(
            "brute-force",
            "Credential brute force",
            "1.0.0",
            "Repeated connection attempts from one source against an authentication service.",
            ThreatType.BRUTE_FORCE,
            ThreatSeverity.HIGH,
            "T1110",
            "Brute Force",
            "Counts connection attempts per source against known authentication ports "
                    + "(SSH, RDP, FTP, SMB, database and mail services) and reports rapid sustained attempts "
                    + "exceeding " + DEFAULT_ATTEMPT_THRESHOLD + " attempts.",
            "Lock or rate-limit the targeted accounts, enforce MFA, block the source address and "
                    + "audit the service's authentication log for successful logins from it.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context, attemptThreshold);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> attempts = new HashMap<>();
        private final Map<String, Integer> portByKey = new HashMap<>();
        private final long threshold;

        private Evaluator(DetectionContext context, long threshold) {
            super(METADATA, context);
            this.threshold = threshold;
        }

        @Override
        public void accept(PacketRecord packet) {
            if (!packet.hasPorts() || !AUTH_SERVICES.containsKey(packet.destinationPort())) {
                return;
            }
            if (!packet.isSynOnly()) {
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
                if (observation.count() < threshold || !canEmit()) {
                    return;
                }
                // False positive mitigation: slow periodic admin logins spread across a long capture
                // window do not represent a brute force attack.
                if (observation.rate() < MIN_ATTEMPT_RATE_PER_SECOND && observation.spanSeconds() > MAX_BENIGN_SPAN_SECONDS) {
                    return;
                }
                Integer port = portByKey.get(key);
                String service = AUTH_SERVICES.getOrDefault(port, "authentication");
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), observation.count() >= SEVERE_ATTEMPT_THRESHOLD),
                        Math.min(0.65d + (observation.count() / 1_000.0d), 0.96d),
                        "Possible " + service + " brute force against " + target,
                        source + " made " + observation.count() + " connection attempts to the "
                                + service + " service on " + target + ":" + port + ".",
                        "service=" + service
                                + "; destinationPort=" + port
                                + "; attempts=" + observation.count()
                                + "; threshold=" + threshold
                                + "; attemptsPerSecond=" + String.format("%.2f", observation.rate())
                                + "; spanSeconds=" + String.format("%.1f", observation.spanSeconds()),
                        source,
                        target,
                        Protocol.TCP,
                        observation);
            });
            return super.finish();
        }
    }
}
