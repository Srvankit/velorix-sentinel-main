package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Exchanges a valid refresh token for a new token pair")
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
