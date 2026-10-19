package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AnalysisResponse", description = "Inspection run over an uploaded capture")
public record AnalysisResponse(
        UUID id,
        UUID uploadedFileId,
        String originalFileName,
        AnalysisStatus status,
        @Schema(description = "Current pipeline stage, drives the progress stepper") AnalysisStage stage,
        @Schema(description = "Completion percentage between 0 and 100") int progressPercent,
        Instant startedAt,
        Instant completedAt,
        Long duration,
        long totalPackets,
        long processedPackets,
        long malformedPackets,
        boolean truncated,
        long maliciousPackets,
        long safePackets,
        double averagePacketSize,
        long uniqueSourceIps,
        long uniqueDestinationIps,
        Instant captureStartedAt,
        Instant captureEndedAt,
        Long captureDuration,
        int riskScore,
        String analysisVersion,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {
}
