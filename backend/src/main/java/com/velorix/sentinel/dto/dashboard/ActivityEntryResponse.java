package com.velorix.sentinel.dto.dashboard;

import com.velorix.sentinel.entity.enums.ActivityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ActivityEntryResponse", description = "One entry of the audit timeline")
public record ActivityEntryResponse(
        UUID id,
        ActivityType activityType,
        String description,
        String ipAddress,
        boolean successful,
        Instant occurredAt) {
}
