package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.dashboard.ActivityEntryResponse;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse;
import com.velorix.sentinel.dto.dashboard.DashboardSummaryResponse;
import com.velorix.sentinel.dto.dashboard.SystemHealthResponse;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API behind the security operations dashboard. Everything is owner scoped and
 * derived from persisted data; empty accounts get zeros and empty arrays.
 */
@RestController
@RequestMapping(ApiConstants.DASHBOARD_BASE)
@Validated
@Tag(name = "Dashboard", description = "Aggregated security posture for the signed-in analyst")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private static final int DEFAULT_LIMIT = 10;

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Headline security counters")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.summary(), "Summary computed"));
    }

    @GetMapping("/charts")
    @Operation(summary = "Chart series for trends, protocols, severity and top talkers")
    public ResponseEntity<ApiResponse<DashboardChartsResponse>> charts() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.charts(), "Charts computed"));
    }

    @GetMapping("/recent-analysis")
    @Operation(summary = "Most recent inspection runs")
    public ResponseEntity<ApiResponse<List<AnalysisResponse>>> recentAnalyses(
            @Parameter(description = "Number of rows, 1-50")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.recentAnalyses(limit), "Runs returned"));
    }

    @GetMapping("/recent-threats")
    @Operation(summary = "Most recent findings")
    public ResponseEntity<ApiResponse<List<ThreatResponse>>> recentThreats(
            @Parameter(description = "Number of rows, 1-50")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.recentThreats(limit), "Threats returned"));
    }

    @GetMapping("/activity")
    @Operation(summary = "Audit timeline")
    public ResponseEntity<ApiResponse<List<ActivityEntryResponse>>> activity(
            @Parameter(description = "Number of rows, 1-50")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.activity(limit), "Activity returned"));
    }

    @GetMapping("/system-health")
    @Operation(summary = "Status of the API, database and inspection worker")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> systemHealth() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.systemHealth(), "Health computed"));
    }
}
