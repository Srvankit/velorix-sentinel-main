package com.velorix.sentinel.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed JWT configuration bound from {@code velorix.security.jwt.*}.
 */
@ConfigurationProperties(prefix = "velorix.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        String tokenPrefix,
        String headerName) {

    public JwtProperties {
        issuer = (issuer == null || issuer.isBlank()) ? "velorix-sentinel" : issuer;
        accessTokenExpiration = accessTokenExpiration == null ? Duration.ofMinutes(15) : accessTokenExpiration;
        refreshTokenExpiration = refreshTokenExpiration == null ? Duration.ofDays(7) : refreshTokenExpiration;
        tokenPrefix = (tokenPrefix == null || tokenPrefix.isBlank()) ? "Bearer " : tokenPrefix;
        headerName = (headerName == null || headerName.isBlank()) ? "Authorization" : headerName;
    }
}
