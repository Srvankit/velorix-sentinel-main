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
 * Volumetric / distributed denial of service against a single target.
 *
 * <p>Two independent signals are combined: how many distinct sources converged on one
 * destination, and how fast packets arrived. Either alone produces false positives — a
 * popular server has many clients, a file transfer has a high rate — but together they
 * describe an attack.</p>
 */
@Component
public class DdosRule implements DetectionRule {

    private static final int SOURCE_THRESHOLD = 50;
    private static final double RATE_THRESHOLD_PER_SECOND = 200.0d;
    private static final long MIN_PACKETS = 500;
    private static final int MAX_SOURCES_TRACKED = 50_000;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "ddos",
            "Distributed denial of service",
            "1.0.0",
            "A large number of sources, or an extreme packet rate, converging on one target.",
            ThreatType.DDOS,
            ThreatSeverity.CRITICAL,
            "T1498",
            "Network Denial of Service",
            "Groups traffic by destination host and reports targets receiving at least "
                    + MIN_PACKETS + " packets with either more than " + SOURCE_THRESHOLD
                    + " distinct sources or more than " + (int) RATE_THRESHOLD_PER_SECOND
                    + " packets per second.",
            "Engage upstream DDoS mitigation, apply rate limiting and connection caps at the edge, "
                    + "and confirm the target's availability during the capture window.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> byTarget = new HashMap<>();
        private final Map<String, Set<String>> sourcesByTarget = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            String target = packet.destinationIp();
            if (target == null) {
                return;
            }
            Observation observation = track(byTarget, target);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            Set<String> sources = sourcesByTarget.computeIfAbsent(target, ignored -> new HashSet<>());
            if (sources.size() < MAX_SOURCES_TRACKED) {
                sources.add(packet.sourceIp());
            }
        }

        @Override
        public List<ThreatCandidate> finish() {
            byTarget.forEach((target, observation) -> {
                if (observation.count() < MIN_PACKETS || !canEmit()) {
                    return;
                }
                Set<String> sources = sourcesByTarget.getOrDefault(target, Set.of());
                int distinctSources = sources.size();
                double rate = observation.rate();
                boolean distributed = distinctSources >= SOURCE_THRESHOLD;
                boolean volumetric = rate >= RATE_THRESHOLD_PER_SECOND;
                if (!distributed && !volumetric) {
                    return;
                }
                String label = distributed && volumetric
                        ? "Distributed flood"
                        : distributed ? "Distributed traffic surge" : "Volumetric flood";
                emit(
                        distributed && volumetric ? ThreatSeverity.CRITICAL : ThreatSeverity.HIGH,
                        Math.min(0.55d + (distributed ? 0.2d : 0.0d) + (volumetric ? 0.2d : 0.0d), 0.98d),
                        label + " against " + target,
                        target + " received " + observation.count() + " packets from " + distinctSources
                                + " distinct sources at " + String.format("%.1f", rate)
                                + " packets per second.",
                        "distinctSources=" + distinctSources
                                + "; sourceThreshold=" + SOURCE_THRESHOLD
                                + "; packets=" + observation.count()
                                + "; packetsPerSecond=" + String.format("%.2f", rate)
                                + "; rateThreshold=" + RATE_THRESHOLD_PER_SECOND
                                + "; bytes=" + observation.bytes(),
                        distinctSources == 1 ? observation.lastSourceIp() : null,
                        target,
                        Protocol.OTHER,
                        observation);
            });
            return super.finish();
        }
    }
}
