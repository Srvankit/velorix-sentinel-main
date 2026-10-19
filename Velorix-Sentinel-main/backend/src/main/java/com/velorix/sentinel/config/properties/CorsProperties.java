package com.velorix.sentinel.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed CORS configuration bound from {@code velorix.cors.*}.
 */
@ConfigurationProperties(prefix = "velorix.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Boolean allowCredentials,
        Long maxAge) {

    public CorsProperties {
        // A blank/unset CORS_ALLOWED_ORIGINS must never silently reject every browser
        // client: fall back to the known Velorix Sentinel console origins.
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ?List.of(
                "http://localhost:8080",
                "http://localhost:5173",
                "http://localhost:3000",
                "https://velorixsentinel.netlify.app",
                "https://*.netlify.app",
                "https://*.lovable.app",
                "https://*.lovableproject.com")
                : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty()
                ? List.of("*")
                : List.copyOf(allowedHeaders);
        exposedHeaders = exposedHeaders == null ? List.of() : List.copyOf(exposedHeaders);
        allowCredentials = allowCredentials == null || allowCredentials;
        maxAge = maxAge == null ? 3600L : maxAge;
    }
}
