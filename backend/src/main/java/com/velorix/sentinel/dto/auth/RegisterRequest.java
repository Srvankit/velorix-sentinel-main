package com.velorix.sentinel.dto.auth;

import com.velorix.sentinel.validation.PasswordsMatch;
import com.velorix.sentinel.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Payload used to create a new Velorix Sentinel account")
@PasswordsMatch
public record RegisterRequest(

        @Schema(example = "Ada") @NotBlank @Size(max = 80) String firstName,

        @Schema(example = "Lovelace") @NotBlank @Size(max = 80) String lastName,

        @Schema(example = "ada@velorix.io") @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Plaintext password, hashed with BCrypt before storage")
        @NotBlank @StrongPassword String password,

        @Schema(description = "Must match the password") @NotBlank String confirmPassword) {
}
