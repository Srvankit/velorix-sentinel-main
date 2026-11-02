package com.velorix.sentinel.dto.auth;

import com.velorix.sentinel.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePasswordRequest", description = "Authenticated request to change account password")
public record ChangePasswordRequest(
        @Schema(description = "Current account password")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "New password meeting complexity policy")
        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword) {
}

