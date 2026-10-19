package com.velorix.sentinel.dto.threat;

import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatStatus;
import com.velorix.sentinel.entity.enums.ThreatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Optional filters for the threat feed. A {@code null} member means "any".
 */
@Schema(name = "ThreatFilter", description = "Filters for the threat feed")
public record ThreatFilter(
        @Schema(description = "Restrict to threats of one analysis") UUID analysisId,
        @Schema(description = "Restrict to one severity") ThreatSeverity severity,
        @Schema(description = "Restrict to one detection category") ThreatType threatType,
        @Schema(description = "Restrict to one triage state") ThreatStatus status,
        @Schema(description = "Restrict to findings produced by one rule")
        @Size(max = 60) String detectionRule,
        @Schema(description = "Match against title, description, source or destination address")
        @Size(max = 120) String search,
        @Schema(description = "Minimum detector confidence, 0 to 1")
        @DecimalMin("0.0") @DecimalMax("1.0") Double minConfidence) {

    /** Lower-cased {@code %term%} pattern, or null when no search term was supplied. */
    public String searchPattern() {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }
}
