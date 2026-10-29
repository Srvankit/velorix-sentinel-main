package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LinkOAuthRequest", description = "Payload to link an authenticated account to an external OAuth provider")
public record LinkOAuthRequest(
        @NotBlank(message = "OAuth provider is required")
        @Size(max = 32, message = "OAuth provider must not exceed 32 characters")
        @Schema(description = "OAuth provider registration ID, e.g. google or github", example = "google")
        String provider,

        @NotBlank(message = "OAuth provider ID is required")
        @Size(max = 255, message = "OAuth provider ID must not exceed 255 characters")
        @Schema(description = "Stable unique subject identifier from the OAuth provider", example = "1049281048291048")
        String providerId) {
}
