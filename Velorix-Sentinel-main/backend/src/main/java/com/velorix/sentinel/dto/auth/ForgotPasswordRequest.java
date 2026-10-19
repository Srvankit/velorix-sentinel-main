package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ForgotPasswordRequest", description = "Requests a password reset email")
public record ForgotPasswordRequest(
        @Schema(example = "ada@velorix.io") @NotBlank @Email @Size(max = 254) String email) {
}
