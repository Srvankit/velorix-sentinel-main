package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.dto.analysis.AnalysisDetailResponse;
import com.velorix.sentinel.dto.analysis.AnalysisFilter;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.analysis.AnalysisSummaryResponse;
import com.velorix.sentinel.dto.analysis.ConversationResponse;
import com.velorix.sentinel.dto.analysis.NetworkSummaryResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.packet.PacketDetailResponse;
import com.velorix.sentinel.dto.packet.PacketFilter;
import com.velorix.sentinel.dto.packet.PacketResponse;
import com.velorix.sentinel.service.AnalysisService;
import com.velorix.sentinel.service.CaptureAnalyticsService;
import com.velorix.sentinel.service.InspectionService;
import com.velorix.sentinel.service.PacketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over inspection runs, their summaries and their decoded frames.
 */
@RestController
@RequestMapping(ApiConstants.ANALYSIS_BASE)
@Tag(name = "Analysis", description = "Deep packet inspection runs")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalysisService analysisService;
    private final PacketService packetService;
    private final InspectionService inspectionService;
    private final CaptureAnalyticsService captureAnalyticsService;

    public AnalysisController(
            AnalysisService analysisService,
            PacketService packetService,
            InspectionService inspectionService,
            CaptureAnalyticsService captureAnalyticsService) {
        this.analysisService = analysisService;
        this.packetService = packetService;
        this.inspectionService = inspectionService;
        this.captureAnalyticsService = captureAnalyticsService;
    }

    @PostMapping("/start/{fileId}")
    @Operation(summary = "Start a deep packet inspection run",
            description = "Accepts the capture for inspection and returns immediately with a queued run. "
                    + "Poll GET /analysis/{id} for stage and progress.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Inspection accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Capture not found or not visible"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Capture is not ready or already being inspected")
    })
    public ResponseEntity<ApiResponse<AnalysisResponse>> start(
            @Parameter(description = "Uploaded capture identifier") @PathVariable UUID fileId) {
        log.info("Inspection requested for capture {}", fileId);
        AnalysisResponse analysis = inspectionService.start(fileId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(analysis, "Inspection started"));
    }

    @GetMapping
    @Operation(summary = "List inspection runs",
            description = "Paginated, owner-scoped listing. Administrators see every run.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analyses returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<ApiResponse<PageResponse<AnalysisResponse>>> list(
            @Valid AnalysisFilter filter,
            @Valid PageRequestParams pageParams) {
        log.debug("Listing analyses with filter {}", filter);
        return ResponseEntity.ok(ApiResponse.success(analysisService.list(filter, pageParams), "Analyses loaded"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one inspection run",
            description = "Returns the run, its capture and its aggregated summary. "
                    + "The summary is null until the run completes.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analysis returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Analysis not found or not visible")
    })
    public ResponseEntity<ApiResponse<AnalysisDetailResponse>> getById(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analysisService.getById(id), "Analysis loaded"));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Fetch the aggregated summary of an inspection run")
    public ResponseEntity<ApiResponse<AnalysisSummaryResponse>> getSummary(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analysisService.getSummary(id), "Summary loaded"));
    }

    @GetMapping("/{id}/packets")
    @Operation(summary = "List decoded frames of an inspection run",
            description = "Always paginated; a capture can contain millions of frames.")
    public ResponseEntity<ApiResponse<PageResponse<PacketResponse>>> listPackets(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id,
            @Valid PacketFilter filter,
            @Valid PageRequestParams pageParams) {
        return ResponseEntity.ok(ApiResponse.success(
                packetService.listByAnalysis(id, filter, pageParams), "Packets loaded"));
    }

    @GetMapping("/{id}/network-summary")
    @Operation(summary = "Traffic statistics of an inspection run",
            description = "Bandwidth, cardinalities, protocol distribution and top-N rankings, "
                    + "computed live from the stored frames.")
    public ResponseEntity<ApiResponse<NetworkSummaryResponse>> networkSummary(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                captureAnalyticsService.networkSummary(id), "Network summary loaded"));
    }

    @GetMapping("/{id}/conversations")
    @Operation(summary = "List conversations of an inspection run",
            description = "Frames folded into flows by source, destination and protocol. "
                    + "Sortable by packets, bytes, startedAt, endedAt, client, server or protocol.")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> conversations(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id,
            @Valid PageRequestParams pageParams) {
        return ResponseEntity.ok(ApiResponse.success(
                captureAnalyticsService.conversations(id, pageParams), "Conversations loaded"));
    }

    @GetMapping("/{id}/packets/{packetId}")
    @Operation(summary = "Fetch every decoded field of one frame")
    public ResponseEntity<ApiResponse<PacketDetailResponse>> getPacket(
            @Parameter(description = "Analysis identifier") @PathVariable UUID id,
            @Parameter(description = "Packet identifier") @PathVariable UUID packetId) {
        return ResponseEntity.ok(ApiResponse.success(
                packetService.getDetail(id, packetId), "Packet loaded"));
    }
}
