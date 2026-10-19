package com.velorix.sentinel.detection;

import com.velorix.sentinel.config.properties.DetectionProperties;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes every enabled detection rule against a stored capture in a single pass.
 *
 * <p>Design constraint: a capture may hold millions of frames, so the engine never
 * materialises the packet table. It streams frames in chunks and fans each one out to
 * every rule evaluator, which keeps cost linear in the number of frames and independent
 * of how many rules are installed.</p>
 *
 * <p>The engine owns persistence of findings and the run's aggregate verdict; rules stay
 * pure and never touch the database.</p>
 */
@Component
public class ThreatDetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(ThreatDetectionEngine.class);

    private final DetectionRuleRegistry registry;
    private final PacketStreamReader packetStreamReader;
    private final ThreatBatchWriter threatBatchWriter;
    private final DetectionProperties properties;

    public ThreatDetectionEngine(
            DetectionRuleRegistry registry,
            PacketStreamReader packetStreamReader,
            ThreatBatchWriter threatBatchWriter,
            DetectionProperties properties) {
        this.registry = registry;
        this.packetStreamReader = packetStreamReader;
        this.threatBatchWriter = threatBatchWriter;
        this.properties = properties;
    }

    /**
     * Scans one run and replaces its findings.
     *
     * @param analysisId   run to scan
     * @param totalPackets frames the inspection engine recorded, used for ratio heuristics
     * @param captureStart first frame timestamp, may be null
     * @param captureEnd   last frame timestamp, may be null
     */
    public DetectionOutcome run(UUID analysisId, long totalPackets, Instant captureStart, Instant captureEnd) {
        long startedAt = System.nanoTime();

        DetectionContext context = new DetectionContext(
                analysisId,
                totalPackets,
                captureStart,
                captureEnd,
                new DetectionLimits(
                        properties.maxFindingsPerRule(),
                        properties.maxSampleFrames(),
                        properties.maxTrackedKeys()));

        List<DetectionRule> rules = registry.enabledRules();
        List<RuleEvaluator> evaluators = new ArrayList<>(rules.size());
        for (DetectionRule rule : rules) {
            evaluators.add(rule.newEvaluator(context));
        }

        long scanned = packetStreamReader.stream(
                analysisId,
                properties.streamChunkSize(),
                packet -> dispatch(evaluators, rules, packet));

        List<ThreatCandidate> findings = collect(rules, evaluators);
        findings.sort(Comparator
                .comparingInt((ThreatCandidate candidate) -> candidate.severity().weight()).reversed()
                .thenComparing(Comparator.comparingDouble(ThreatCandidate::confidence).reversed()));

        threatBatchWriter.deleteExisting(analysisId);
        persist(analysisId, findings);

        Map<ThreatSeverity, Long> histogram = histogram(findings);
        long critical = histogram.getOrDefault(ThreatSeverity.CRITICAL, 0L);
        long high = histogram.getOrDefault(ThreatSeverity.HIGH, 0L);
        long medium = histogram.getOrDefault(ThreatSeverity.MEDIUM, 0L);
        long low = histogram.getOrDefault(ThreatSeverity.LOW, 0L);
        int riskScore = RiskScorer.score(critical, high, medium, low);
        OverallStatus status = RiskScorer.status(riskScore, critical, high);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        log.info("Detection finished for analysis {}: {} findings from {} rules over {} packets in {} ms "
                        + "(risk={}, status={})",
                analysisId, findings.size(), rules.size(), scanned, durationMs, riskScore, status);

        return new DetectionOutcome(
                analysisId, scanned, rules.size(), findings.size(),
                critical, high, medium, low, riskScore, status, durationMs);
    }

    /**
     * Hands one frame to every evaluator. A rule that throws is isolated: a single buggy
     * heuristic must not abort the whole scan, so the failure is logged once per pass and
     * the remaining rules keep running.
     */
    private void dispatch(List<RuleEvaluator> evaluators, List<DetectionRule> rules, PacketRecord packet) {
        for (int i = 0; i < evaluators.size(); i++) {
            try {
                evaluators.get(i).accept(packet);
            } catch (RuntimeException ex) {
                log.warn("Detection rule {} failed on packet {} and was skipped for that frame: {}",
                        rules.get(i).metadata().id(), packet.number(), ex.toString());
            }
        }
    }

    private List<ThreatCandidate> collect(List<DetectionRule> rules, List<RuleEvaluator> evaluators) {
        List<ThreatCandidate> findings = new ArrayList<>();
        for (int i = 0; i < evaluators.size(); i++) {
            try {
                findings.addAll(evaluators.get(i).finish());
            } catch (RuntimeException ex) {
                log.warn("Detection rule {} failed while producing findings: {}",
                        rules.get(i).metadata().id(), ex.toString());
            }
        }
        return findings;
    }

    private void persist(UUID analysisId, List<ThreatCandidate> findings) {
        int batchSize = properties.batchSize();
        for (int from = 0; from < findings.size(); from += batchSize) {
            int to = Math.min(from + batchSize, findings.size());
            threatBatchWriter.write(analysisId, findings.subList(from, to));
        }
    }

    private Map<ThreatSeverity, Long> histogram(List<ThreatCandidate> findings) {
        Map<ThreatSeverity, Long> histogram = new EnumMap<>(ThreatSeverity.class);
        for (ThreatCandidate candidate : findings) {
            histogram.merge(candidate.severity(), 1L, Long::sum);
        }
        return histogram;
    }
}
