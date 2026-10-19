package com.velorix.sentinel.dto.file;

import com.velorix.sentinel.entity.enums.UploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional filters for the uploaded-file listing. A {@code null} member means "any".
 */
@Schema(name = "UploadedFileFilter", description = "Filters for the capture listing")
public record UploadedFileFilter(
        @Schema(description = "Restrict to a single upload status") UploadStatus status,

        @Schema(description = "Case insensitive match on the original file name")
        @Size(max = 255, message = "Search term must not exceed 255 characters")
        String search) {

    /** Normalises a blank search term to {@code null} so the query treats it as "any". */
    public String normalisedSearch() {
        return search == null || search.isBlank() ? null : search.trim();
    }
}
