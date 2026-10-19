package com.velorix.sentinel.mapper;

import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.analysis.AnalysisSummaryResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping for the {@link Analysis} aggregate and its summary.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AnalysisMapper {

    @Mapping(target = "uploadedFileId", source = "uploadedFile.id")
    @Mapping(target = "originalFileName", source = "uploadedFile.originalFileName")
    AnalysisResponse toResponse(Analysis analysis);

    List<AnalysisResponse> toResponseList(List<Analysis> analyses);

    @Mapping(target = "analysisId", source = "analysis.id")
    AnalysisSummaryResponse toSummaryResponse(AnalysisSummary summary);
}
