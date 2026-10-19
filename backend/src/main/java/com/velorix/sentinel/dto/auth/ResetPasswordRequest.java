package com.velorix.sentinel.dto.auth;

import com.velorix.sentinel.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ResetPasswordRequest", description = "Completes a password reset using an emailed token")
public record ResetPasswordRequest(

        @Schema(description = "Single use token delivered by email") @NotBlank String token,

        @NotBlank @StrongPassword String newPassword) {
}
