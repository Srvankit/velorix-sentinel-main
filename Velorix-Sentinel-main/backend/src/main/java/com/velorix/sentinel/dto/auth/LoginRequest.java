package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credentials exchanged for an access and refresh token pair")
public record LoginRequest(

        @Schema(example = "ada@velorix.io") @NotBlank @Email @Size(max = 254) String email,

        @NotBlank @Size(max = 72) String password,

        @Schema(description = "Issues a longer lived refresh token when true") boolean rememberMe) {
}
