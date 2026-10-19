package com.velorix.sentinel.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Live status of the moving parts the analyst depends on.
 */
@Schema(name = "SystemHealthResponse", description = "Platform component health")
public record SystemHealthResponse(
        ComponentHealth api,
        ComponentHealth database,
        ComponentHealth worker,
        Instant checkedAt) {

    @Schema(name = "ComponentHealth", description = "Status of a single platform component")
    public record ComponentHealth(
            String name,
            @Schema(allowableValues = {"UP", "DEGRADED", "DOWN"}) String status,
            String detail) {

        public static ComponentHealth up(String name, String detail) {
            return new ComponentHealth(name, "UP", detail);
        }

        public static ComponentHealth degraded(String name, String detail) {
            return new ComponentHealth(name, "DEGRADED", detail);
        }

        public static ComponentHealth down(String name, String detail) {
            return new ComponentHealth(name, "DOWN", detail);
        }
    }
}
