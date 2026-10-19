package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "AuthResponse", description = "Issued token pair together with the authenticated profile")
public record AuthResponse(
        @Schema(description = "Short lived JWT access token") String accessToken,
        @Schema(description = "Long lived rotating refresh token") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token expiry, UTC") Instant accessTokenExpiresAt,
        @Schema(description = "Refresh token expiry, UTC") Instant refreshTokenExpiresAt,
        UserResponse user) {
}
