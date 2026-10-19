package com.velorix.sentinel.config.properties;

import java.util.Collections;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Threat detection engine settings bound from {@code velorix.detection.*}.
 *
 * <p>Every threshold here is a tuning knob for a heuristic, not a hard rule. They are
 * configuration and not constants because what counts as "many connections" differs
 * wildly between a lab capture and a datacentre span port.</p>
 *
 * @param engineVersion       stamped onto every finding so results stay traceable
 * @param batchSize           threat rows accumulated before a JDBC batch flush
 * @param streamChunkSize     packets pulled per keyset page while streaming a capture
 * @param maxFindingsPerRule  ceiling on findings a single rule may emit for one run
 * @param maxSampleFrames     packet numbers retained as evidence per finding
 * @param maxTrackedKeys      per-rule cardinality ceiling, guarding worker memory
 * @param disabledRules       rule identifiers switched off at startup
 */
@ConfigurationProperties(prefix = "velorix.detection")
public record DetectionProperties(
        String engineVersion,
        Integer batchSize,
        Integer streamChunkSize,
        Integer maxFindingsPerRule,
        Integer maxSampleFrames,
        Integer maxTrackedKeys,
        Set<String> disabledRules) {

    public DetectionProperties {
        engineVersion = engineVersion == null || engineVersion.isBlank() ? "detect-1.0.0" : engineVersion;
        batchSize = positiveOr(batchSize, 500);
        streamChunkSize = positiveOr(streamChunkSize, 10_000);
        maxFindingsPerRule = positiveOr(maxFindingsPerRule, 200);
        maxSampleFrames = positiveOr(maxSampleFrames, 20);
        maxTrackedKeys = positiveOr(maxTrackedKeys, 200_000);
        disabledRules = disabledRules == null ? Set.of() : Set.copyOf(disabledRules);
    }

    public Set<String> disabledRulesOrEmpty() {
        return disabledRules == null ? Collections.emptySet() : disabledRules;
    }

    private static Integer positiveOr(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
