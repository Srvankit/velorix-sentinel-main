package com.velorix.sentinel.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application level settings bound from {@code velorix.app.*}.
 */
@ConfigurationProperties(prefix = "velorix.app")
public record AppProperties(
        String frontendBaseUrl,
        Duration emailVerificationExpiration,
        Duration passwordResetExpiration,
        Duration shortLivedRefreshExpiration) {

    public AppProperties {
        frontendBaseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? "http://localhost:5173" : stripTrailingSlash(frontendBaseUrl);
        emailVerificationExpiration =
                emailVerificationExpiration == null ? Duration.ofHours(24) : emailVerificationExpiration;
        passwordResetExpiration =
                passwordResetExpiration == null ? Duration.ofMinutes(30) : passwordResetExpiration;
        shortLivedRefreshExpiration =
                shortLivedRefreshExpiration == null ? Duration.ofHours(12) : shortLivedRefreshExpiration;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
