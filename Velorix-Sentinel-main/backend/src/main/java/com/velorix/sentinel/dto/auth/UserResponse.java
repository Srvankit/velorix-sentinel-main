package com.velorix.sentinel.dto.auth;

import com.velorix.sentinel.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserResponse", description = "Public representation of a Velorix Sentinel user")
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        Role role,
        boolean verified,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
