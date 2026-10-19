package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "VerifyEmailRequest", description = "Confirms an email address using the emailed token")
public record VerifyEmailRequest(
        @Schema(description = "Single use token delivered by email") @NotBlank String token) {
}
