package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.dto.file.UploadedFileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Full analysis projection returned by {@code GET /api/v1/analysis/{id}}.
 *
 * <p>Packets and threats are intentionally not inlined: both are unbounded and are
 * served by their own paginated endpoints.</p>
 */
@Schema(name = "AnalysisDetailResponse", description = "Analysis with its capture and aggregated summary")
public record AnalysisDetailResponse(
        AnalysisResponse analysis,
        UploadedFileResponse uploadedFile,
        AnalysisSummaryResponse summary) {
}
