package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Optional filters for the analysis listing. A {@code null} member means "any".
 */
@Schema(name = "AnalysisFilter", description = "Filters for the analysis listing")
public record AnalysisFilter(
        @Schema(description = "Restrict to a single lifecycle status") AnalysisStatus status,
        @Schema(description = "Restrict to analyses of one uploaded capture") UUID uploadedFileId) {
}
