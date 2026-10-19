package com.velorix.sentinel.service;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.analysis.AnalysisDetailResponse;
import com.velorix.sentinel.dto.analysis.AnalysisFilter;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.analysis.AnalysisSummaryResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import java.util.UUID;

/**
 * Read surface over inspection runs. Packet parsing and scoring are implemented in the
 * inspection-engine milestone; this contract is the stable API those results are served through.
 */
public interface AnalysisService {

    PageResponse<AnalysisResponse> list(AnalysisFilter filter, PageRequestParams pageParams);

    AnalysisDetailResponse getById(UUID analysisId);

    AnalysisSummaryResponse getSummary(UUID analysisId);
}
