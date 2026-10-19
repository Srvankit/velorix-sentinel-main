package com.velorix.sentinel.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Single, reusable envelope returned by every endpoint of the platform.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Standard Velorix Sentinel API response envelope")
public record ApiResponse<T>(
        @Schema(description = "Whether the request succeeded") boolean success,
        @Schema(description = "Human readable outcome message") String message,
        @Schema(description = "Response payload, null on failure") T data,
        @Schema(description = "Server UTC timestamp of the response") Instant timestamp,
        @Schema(description = "HTTP status code") int status,
        @Schema(description = "Field or domain level errors, present only on failure") List<ApiError> errors) {

    public static <T> ApiResponse<T> success(T data) {
        return success(data, com.velorix.sentinel.constants.MessageConstants.GENERIC_SUCCESS, HttpStatus.OK);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return success(data, message, HttpStatus.OK);
    }

    public static <T> ApiResponse<T> success(T data, String message, HttpStatus status) {
        return new ApiResponse<>(true, message, data, Instant.now(), status.value(), null);
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus status) {
        return error(message, status, null);
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus status, List<ApiError> errors) {
        return new ApiResponse<>(false, message, null, Instant.now(), status.value(),
                errors == null || errors.isEmpty() ? null : List.copyOf(errors));
    }
}
