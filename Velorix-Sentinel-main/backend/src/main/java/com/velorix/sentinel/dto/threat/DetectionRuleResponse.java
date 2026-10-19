package com.velorix.sentinel.dto.threat;

import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public description of one detection rule, served by the rule catalogue endpoint.
 */
@Schema(name = "DetectionRuleResponse", description = "A rule available to the detection engine")
public record DetectionRuleResponse(
        String id,
        String name,
        String version,
        String description,
        ThreatType threatType,
        ThreatSeverity baseSeverity,
        String mitreTechnique,
        String mitreTechniqueName,
        @Schema(description = "Plain language explanation of how the rule decides") String detectionLogic,
        String recommendation,
        boolean enabled) {
}
