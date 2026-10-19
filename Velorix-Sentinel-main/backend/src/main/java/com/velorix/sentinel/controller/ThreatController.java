package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.threat.DetectionRuleResponse;
import com.velorix.sentinel.dto.threat.DetectionRunResponse;
import com.velorix.sentinel.dto.threat.ThreatFilter;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.dto.threat.ThreatStatsResponse;
import com.velorix.sentinel.service.DetectionService;
import com.velorix.sentinel.service.ThreatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over detected threats, plus the trigger for a detection pass.
 */
@RestController
@RequestMapping(ApiConstants.THREATS_BASE)
@Tag(name = "Threats", description = "Findings raised by the detection engine")
@SecurityRequirement(name = "bearerAuth")
public class ThreatController {

    private static final Logger log = LoggerFactory.getLogger(ThreatController.class);

    private final ThreatService threatService;
    private final DetectionService detectionService;

    public ThreatController(ThreatService threatService, DetectionService detectionService) {
        this.threatService = threatService;
        this.detectionService = detectionService;
    }

    @GetMapping
    @Operation(summary = "List detected threats",
            description = "Owner-scoped threat feed, filterable by analysis, severity, category, "
                    + "rule, triage state, confidence and free text.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Threats returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<ApiResponse<PageResponse<ThreatResponse>>> list(
            @Valid ThreatFilter filter,
            @Valid PageRequestParams pageParams) {
        log.debug("Listing threats with filter {}", filter);
        return ResponseEntity.ok(ApiResponse.success(threatService.list(filter, pageParams), "Threats loaded"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregated threat statistics",
            description = "Severity and category histograms, top talkers and a detection timeline. "
                    + "Omit the analysis id to aggregate across every run visible to the caller.")
    public ResponseEntity<ApiResponse<ThreatStatsResponse>> stats(
            @Parameter(description = "Restrict statistics to one analysis")
            @RequestParam(required = false) UUID analysisId) {
        return ResponseEntity.ok(ApiResponse.success(threatService.stats(analysisId), "Threat statistics loaded"));
    }

    @GetMapping("/rules")
    @Operation(summary = "Detection rule catalogue",
            description = "Every installed rule with its category, MITRE mapping and enablement state.")
    public ResponseEntity<ApiResponse<List<DetectionRuleResponse>>> rules() {
        return ResponseEntity.ok(ApiResponse.success(threatService.rules(), "Detection rules loaded"));
    }

    @PostMapping("/run/{analysisId}")
    @Operation(summary = "Run detection over an analysis",
            description = "Executes every enabled rule against the stored packets of a completed "
                    + "inspection and replaces that run's findings and summary.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detection completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Run is not ready for detection"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Analysis not found or not visible")
    })
    public ResponseEntity<ApiResponse<DetectionRunResponse>> run(
            @Parameter(description = "Analysis identifier") @PathVariable UUID analysisId) {
        log.info("Detection requested for analysis {}", analysisId);
        return ResponseEntity.ok(ApiResponse.success(
                detectionService.run(analysisId), "Detection completed"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single threat")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Threat returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Threat not found or not visible")
    })
    public ResponseEntity<ApiResponse<ThreatResponse>> getById(
            @Parameter(description = "Threat identifier") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(threatService.getById(id), "Threat loaded"));
    }
}
