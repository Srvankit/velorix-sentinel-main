package com.velorix.sentinel.service;

import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.dashboard.ActivityEntryResponse;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse;
import com.velorix.sentinel.dto.dashboard.DashboardSummaryResponse;
import com.velorix.sentinel.dto.dashboard.SystemHealthResponse;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import java.util.List;

/**
 * Read-only aggregation surface behind the security operations dashboard.
 *
 * <p>Every method is owner scoped: administrators see the whole platform, everyone else
 * sees only their own captures.</p>
 */
public interface DashboardService {

    DashboardSummaryResponse summary();

    DashboardChartsResponse charts();

    List<AnalysisResponse> recentAnalyses(int limit);

    List<ThreatResponse> recentThreats(int limit);

    List<ActivityEntryResponse> activity(int limit);

    SystemHealthResponse systemHealth();
}
