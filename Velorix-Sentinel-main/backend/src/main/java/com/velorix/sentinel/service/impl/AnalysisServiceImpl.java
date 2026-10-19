package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.analysis.AnalysisDetailResponse;
import com.velorix.sentinel.dto.analysis.AnalysisFilter;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.analysis.AnalysisSummaryResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.mapper.AnalysisMapper;
import com.velorix.sentinel.mapper.UploadedFileMapper;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.AnalysisService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped reads over inspection runs and their aggregated summaries.
 */
@Service
@Transactional(readOnly = true)
public class AnalysisServiceImpl implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);
    private static final String RESOURCE = "Analysis";

    private final AnalysisRepository analysisRepository;
    private final AnalysisSummaryRepository analysisSummaryRepository;
    private final AnalysisMapper analysisMapper;
    private final UploadedFileMapper uploadedFileMapper;
    private final CurrentUserProvider currentUserProvider;

    public AnalysisServiceImpl(
            AnalysisRepository analysisRepository,
            AnalysisSummaryRepository analysisSummaryRepository,
            AnalysisMapper analysisMapper,
            UploadedFileMapper uploadedFileMapper,
            CurrentUserProvider currentUserProvider) {
        this.analysisRepository = analysisRepository;
        this.analysisSummaryRepository = analysisSummaryRepository;
        this.analysisMapper = analysisMapper;
        this.uploadedFileMapper = uploadedFileMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public PageResponse<AnalysisResponse> list(AnalysisFilter filter, PageRequestParams pageParams) {
        UUID ownerScope = currentUserProvider.ownerScope();
        Page<Analysis> page = analysisRepository.search(
                ownerScope,
                filter == null ? null : filter.status(),
                filter == null ? null : filter.uploadedFileId(),
                pageParams.toPageable());
        log.debug("Listed {} analyses (ownerScope={})", page.getTotalElements(), ownerScope);
        return PageResponse.from(page, analysisMapper::toResponse);
    }

    @Override
    public AnalysisDetailResponse getById(UUID analysisId) {
        Analysis analysis = loadOwned(analysisId);
        return new AnalysisDetailResponse(
                analysisMapper.toResponse(analysis),
                uploadedFileMapper.toResponse(analysis.getUploadedFile()),
                summaryOf(analysisId));
    }

    @Override
    public AnalysisSummaryResponse getSummary(UUID analysisId) {
        loadOwned(analysisId);
        return summaryOf(analysisId);
    }

    /** Loads an analysis the caller is allowed to see, or raises a 404. */
    private Analysis loadOwned(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        return (ownerScope == null
                ? analysisRepository.findWithDetailsById(analysisId)
                : analysisRepository.findByIdAndOwner(analysisId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));
    }

    /** Null while the run has not produced a summary yet - not an error. */
    private AnalysisSummaryResponse summaryOf(UUID analysisId) {
        return analysisSummaryRepository.findByAnalysisId(analysisId)
                .map(analysisMapper::toSummaryResponse)
                .orElse(null);
    }
}
