package com.velorix.sentinel.detection.support;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Boilerplate shared by every rule evaluator: bounded finding accumulation and bounded
 * key tracking.
 *
 * <p>Both ceilings exist because rule input is attacker supplied. A capture crafted to
 * contain a million distinct source addresses must degrade into "we stopped tracking",
 * never into an out of memory error that takes the whole worker down.</p>
 */
public abstract class AbstractRuleEvaluator implements RuleEvaluator {

    protected final RuleMetadata rule;
    protected final DetectionContext context;

    private final List<ThreatCandidate> findings = new ArrayList<>();
    private boolean trackingCapped;

    protected AbstractRuleEvaluator(RuleMetadata rule, DetectionContext context) {
        this.rule = rule;
        this.context = context;
    }

    @Override
    public List<ThreatCandidate> finish() {
        return List.copyOf(findings);
    }

    /** False once the rule has emitted its allowance of findings for this run. */
    protected boolean canEmit() {
        return findings.size() < context.limits().maxFindingsPerRule();
    }

    protected int maxSamples() {
        return context.limits().maxSampleFrames();
    }

    /** True when key tracking hit its ceiling and results are therefore partial. */
    protected boolean trackingCapped() {
        return trackingCapped;
    }

    /**
     * Returns the observation for {@code key}, creating it when there is room. Returns
     * {@code null} once the tracking ceiling is reached, which callers treat as "ignore
     * this frame".
     */
    protected <K> Observation track(Map<K, Observation> tracked, K key) {
        Observation existing = tracked.get(key);
        if (existing != null) {
            return existing;
        }
        if (tracked.size() >= context.limits().maxTrackedKeys()) {
            trackingCapped = true;
            return null;
        }
        Observation created = new Observation(maxSamples());
        tracked.put(key, created);
        return created;
    }

    protected void emit(
            ThreatSeverity severity,
            double confidence,
            String title,
            String description,
            String evidence,
            String sourceIp,
            String destinationIp,
            Protocol protocol,
            Observation observation) {
        emit(severity, confidence, title, description, evidence, sourceIp, destinationIp, protocol,
                observation.count(), observation.samples(), observation.firstSeen(), observation.lastSeen());
    }

    protected void emit(
            ThreatSeverity severity,
            double confidence,
            String title,
            String description,
            String evidence,
            String sourceIp,
            String destinationIp,
            Protocol protocol,
            long packetCount,
            List<Long> samples,
            java.time.Instant firstSeen,
            java.time.Instant lastSeen) {
        if (!canEmit()) {
            return;
        }
        findings.add(new ThreatCandidate(
                rule,
                rule.threatType(),
                severity,
                confidence,
                title,
                description,
                evidence,
                rule.recommendation(),
                sourceIp,
                destinationIp,
                protocol,
                packetCount,
                samples,
                firstSeen,
                lastSeen));
    }

    /** Raises severity one step, saturating at CRITICAL. */
    protected static ThreatSeverity escalate(ThreatSeverity base, boolean condition) {
        if (!condition) {
            return base;
        }
        return switch (base) {
            case LOW -> ThreatSeverity.MEDIUM;
            case MEDIUM -> ThreatSeverity.HIGH;
            case HIGH, CRITICAL -> ThreatSeverity.CRITICAL;
        };
    }
}
