package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;

/**
 * Static description of a detection rule.
 *
 * <p>Metadata is deliberately separate from the detection logic: the rule catalogue,
 * enablement and versioning are all served from this record without ever executing a
 * rule.</p>
 *
 * @param id              stable, kebab-case identifier persisted on every finding
 * @param name            analyst facing rule name
 * @param version         semantic version of the detection logic; bump on behaviour change
 * @param description     what the rule looks for, in one sentence
 * @param threatType      category every finding of this rule carries
 * @param baseSeverity    severity used when the rule does not escalate
 * @param mitreTechnique  MITRE ATT&CK technique id, null when unmapped
 * @param mitreName       MITRE ATT&CK technique name, null when unmapped
 * @param detectionLogic  plain language explanation shown in the threat detail panel
 * @param recommendation  default mitigation advice
 */
public record RuleMetadata(
        String id,
        String name,
        String version,
        String description,
        ThreatType threatType,
        ThreatSeverity baseSeverity,
        String mitreTechnique,
        String mitreName,
        String detectionLogic,
        String recommendation) {
}
