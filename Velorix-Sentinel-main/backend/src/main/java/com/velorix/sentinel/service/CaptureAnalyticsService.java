package com.velorix.sentinel.service;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.analysis.ConversationResponse;
import com.velorix.sentinel.dto.analysis.NetworkSummaryResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import java.util.UUID;

/**
 * Aggregate reads derived from the frames of a single inspection run: traffic statistics,
 * rankings and conversations. Everything is computed from stored packets.
 */
public interface CaptureAnalyticsService {

    NetworkSummaryResponse networkSummary(UUID analysisId);

    PageResponse<ConversationResponse> conversations(UUID analysisId, PageRequestParams pageParams);
}
