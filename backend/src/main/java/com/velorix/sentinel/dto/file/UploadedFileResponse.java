package com.velorix.sentinel.dto.file;

import com.velorix.sentinel.entity.enums.UploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "UploadedFileResponse", description = "A capture uploaded for inspection")
public record UploadedFileResponse(
        UUID id,
        UUID ownerId,
        String originalFileName,
        String storedFileName,
        long fileSize,
        String fileType,
        UploadStatus uploadStatus,
        String checksum,
        Instant createdAt,
        Instant updatedAt) {
}
