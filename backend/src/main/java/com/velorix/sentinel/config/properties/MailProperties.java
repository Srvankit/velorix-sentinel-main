package com.velorix.sentinel.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for transactional email delivery bound from {@code velorix.mail.*}.
 */
@ConfigurationProperties(prefix = "velorix.mail")
public record MailProperties(
        String from,
        String fromName) {

    public MailProperties {
        from = (from == null || from.isBlank()) ? "noreply@velorix.com" : from.trim();
        fromName = (fromName == null || fromName.isBlank()) ? "Velorix Sentinel" : fromName.trim();
    }
}
