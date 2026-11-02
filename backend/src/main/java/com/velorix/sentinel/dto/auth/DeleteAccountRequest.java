package com.velorix.sentinel.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DeleteAccountRequest", description = "Confirmation payload required to permanently delete an account")
public record DeleteAccountRequest(
        @Schema(description = "Account password for re-authentication")
        String password,

        @Schema(description = "Confirmation phrase, e.g. 'DELETE'")
        String confirmation) {
}

