package com.velorix.sentinel.service;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.threat.DetectionRuleResponse;
import com.velorix.sentinel.dto.threat.ThreatFilter;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.dto.threat.ThreatStatsResponse;
import java.util.List;
import java.util.UUID;

/**
 * Read surface over detected threats, always scoped to the caller.
 */
public interface ThreatService {

    PageResponse<ThreatResponse> list(ThreatFilter filter, PageRequestParams pageParams);

    ThreatResponse getById(UUID threatId);

    /** Aggregated counters for dashboards; {@code analysisId} may be null for "all runs". */
    ThreatStatsResponse stats(UUID analysisId);

    /** Catalogue of installed detection rules, including disabled ones. */
    List<DetectionRuleResponse> rules();
}
