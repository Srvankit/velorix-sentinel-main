package com.velorix.sentinel.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single error detail attached to a failed {@link ApiResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Field or domain level error detail")
public record ApiError(
        @Schema(description = "Field name when the error is field scoped", example = "email") String field,
        @Schema(description = "Stable machine readable error code", example = "VALIDATION_ERROR") String code,
        @Schema(description = "Human readable error message") String message) {

    public static ApiError of(String code, String message) {
        return new ApiError(null, code, message);
    }

    public static ApiError ofField(String field, String code, String message) {
        return new ApiError(field, code, message);
    }
}
