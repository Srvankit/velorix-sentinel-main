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
 * Command-and-control beaconing: contact with an external host at machine-regular
 * intervals.
 *
 * <p>Humans browse irregularly; implants check in on a timer. The rule therefore scores
 * the <em>consistency</em> of inter-packet gaps (their coefficient of variation) rather
 * than their length, so it catches a 30 second beacon and an hourly one alike.</p>
 */
@Component
public class BeaconingRule implements DetectionRule {

    private static final int MIN_INTERVALS = 10;
    /** Standard deviation below this fraction of the mean gap is machine-like. */
    private static final double MAX_VARIATION = 0.20d;
    private static final double MIN_MEAN_INTERVAL_SECONDS = 1.0d;
    private static final double MAX_MEAN_INTERVAL_SECONDS = 3_600.0d;

    private static final RuleMetadata METADATA = new RuleMetadata(
            "beaconing",
            "C2 beaconing",
            "1.0.0",
            "Highly regular, timer-driven contact between an internal host and an external address.",
            ThreatType.BEACONING,
            ThreatSeverity.HIGH,
            "T1071",
            "Application Layer Protocol",
            "Measures the interval between successive packets for each internal-to-external pair "
                    + "and reports pairs with at least " + MIN_INTERVALS
                    + " intervals whose coefficient of variation is below "
                    + (int) (MAX_VARIATION * 100) + "%.",
            "Treat the external address as a candidate C2 endpoint: block it, isolate the internal "
                    + "host and hunt for persistence mechanisms on it.");

    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public RuleEvaluator newEvaluator(DetectionContext context) {
        return new Evaluator(context);
    }

    /** Running interval statistics; Welford is unnecessary at these magnitudes. */
    private static final class IntervalStats {
        private long lastEpochMs = -1L;
        private long intervals;
        private double sumSeconds;
        private double sumSquaredSeconds;

        void observe(long epochMs) {
            if (lastEpochMs >= 0) {
                double seconds = (epochMs - lastEpochMs) / 1000.0d;
                if (seconds > 0) {
                    intervals++;
                    sumSeconds += seconds;
                    sumSquaredSeconds += seconds * seconds;
                }
            }
            lastEpochMs = epochMs;
        }

        double mean() {
            return intervals == 0 ? 0.0d : sumSeconds / intervals;
        }

        double standardDeviation() {
            if (intervals < 2) {
                return 0.0d;
            }
            double mean = mean();
            double variance = Math.max((sumSquaredSeconds / intervals) - (mean * mean), 0.0d);
            return Math.sqrt(variance);
        }

        double coefficientOfVariation() {
            double mean = mean();
            return mean <= 0 ? 1.0d : standardDeviation() / mean;
        }
    }

    private static final class Evaluator extends AbstractRuleEvaluator {

        private final Map<String, Observation> pairs = new HashMap<>();
        private final Map<String, IntervalStats> stats = new HashMap<>();

        private Evaluator(DetectionContext context) {
            super(METADATA, context);
        }

        @Override
        public void accept(PacketRecord packet) {
            if (packet.timestamp() == null) {
                return;
            }
            if (!NetworkAddresses.isPrivate(packet.sourceIp())
                    || !NetworkAddresses.isExternal(packet.destinationIp())) {
                return;
            }
            String key = packet.sourceIp() + '>' + packet.destinationIp();
            Observation observation = track(pairs, key);
            if (observation == null) {
                return;
            }
            observation.record(packet);
            stats.computeIfAbsent(key, ignored -> new IntervalStats())
                    .observe(packet.timestamp().toEpochMilli());
        }

        @Override
        public List<ThreatCandidate> finish() {
            pairs.forEach((key, observation) -> {
                IntervalStats interval = stats.get(key);
                if (interval == null || interval.intervals < MIN_INTERVALS || !canEmit()) {
                    return;
                }
                double mean = interval.mean();
                if (mean < MIN_MEAN_INTERVAL_SECONDS || mean > MAX_MEAN_INTERVAL_SECONDS) {
                    return;
                }
                double variation = interval.coefficientOfVariation();
                if (variation > MAX_VARIATION) {
                    return;
                }
                String source = observation.lastSourceIp();
                String target = observation.lastDestinationIp();
                emit(
                        escalate(METADATA.baseSeverity(), variation <= MAX_VARIATION / 2),
                        Math.min(0.6d + (MAX_VARIATION - variation) * 1.5d, 0.94d),
                        "Beaconing from " + source + " to " + target,
                        source + " contacted " + target + " every "
                                + String.format("%.1f", mean) + " seconds with only "
                                + String.format("%.1f", variation * 100) + "% timing jitter, "
                                + "a pattern characteristic of automated command-and-control check-ins.",
                        "intervals=" + interval.intervals
                                + "; meanIntervalSeconds=" + String.format("%.2f", mean)
                                + "; stdDevSeconds=" + String.format("%.2f", interval.standardDeviation())
                                + "; coefficientOfVariation=" + String.format("%.3f", variation)
                                + "; maxVariation=" + MAX_VARIATION
                                + "; packets=" + observation.count(),
                        source,
                        target,
                        Protocol.OTHER,
                        observation);
            });
            return super.finish();
        }
    }
}
