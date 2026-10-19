package com.velorix.sentinel.mapper;

import com.velorix.sentinel.detection.DetectionRule;
import com.velorix.sentinel.dto.threat.DetectionRuleResponse;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.entity.Threat;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping between the {@link Threat} entity and its DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ThreatMapper {

    @Mapping(target = "analysisId", source = "analysis.id")
    @Mapping(target = "samplePacketNumbers", source = "samplePacketNumbers", qualifiedByName = "parseSamples")
    @Mapping(target = "confidencePercent",
            expression = "java((int) Math.round(threat.getConfidenceScore() * 100))")
    ThreatResponse toResponse(Threat threat);

    List<ThreatResponse> toResponseList(List<Threat> threats);

    /** Stored as a compact CSV; exposed as a typed list. */
    @Named("parseSamples")
    default List<Long> parseSamples(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Long> numbers = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                numbers.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // A malformed sample must never break the read path.
            }
        }
        return List.copyOf(numbers);
    }

    /** Rules are code, not entities, so the catalogue is mapped by hand. */
    default DetectionRuleResponse toRuleResponse(DetectionRule rule, boolean enabled) {
        var metadata = rule.metadata();
        return new DetectionRuleResponse(
                metadata.id(),
                metadata.name(),
                metadata.version(),
                metadata.description(),
                metadata.threatType(),
                metadata.baseSeverity(),
                metadata.mitreTechnique(),
                metadata.mitreName(),
                metadata.detectionLogic(),
                metadata.recommendation(),
                enabled);
    }
}
