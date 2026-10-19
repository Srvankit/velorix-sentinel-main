package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One aggregated conversation (flow) between two endpoints inside a capture.
 *
 * @param status SUSPICIOUS when at least one frame of the flow was flagged, otherwise CLEAN
 */
@Schema(name = "ConversationResponse", description = "Aggregated flow between two endpoints")
public record ConversationResponse(
        String client,
        String server,
        Integer serverPort,
        Protocol protocol,
        long packets,
        long bytes,
        long suspiciousPackets,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String status) {
}
