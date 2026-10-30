package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "OAuthExchangeRequest", description = "Payload to exchange a single-use OAuth authorization code for session tokens")
public record OAuthExchangeRequest(
        @NotBlank(message = "OAuth exchange code is required")
        @Size(max = 256, message = "OAuth exchange code must not exceed 256 characters")
        @Schema(description = "Single-use authorization code from OAuth callback", example = "a1b2c3d4e5f6...")
        String code) {
}
