package com.velorix.sentinel.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for automated database lifecycle and retention purging.
 *
 * <p>All durations use standard ISO-8601 formatting (e.g. {@code P30D} for 30 days,
 * {@code P7D} for 7 days, {@code P90D} for 90 days).</p>
 */
@ConfigurationProperties(prefix = "velorix.retention")
public record RetentionProperties(
        boolean enabled,
        String cron,
        String zone,
        int batchSize,
        Duration refreshTokenRetention,
        Duration verificationTokenRetention,
        Duration activityLogRetention,
        Duration softDeletedFileRetention) {

    public RetentionProperties {
        cron = (cron == null || cron.isBlank()) ? "0 0 3 * * *" : cron;
        zone = (zone == null || zone.isBlank()) ? "UTC" : zone;
        batchSize = batchSize <= 0 ? 1000 : batchSize;
        refreshTokenRetention = refreshTokenRetention == null ? Duration.ofDays(30) : refreshTokenRetention;
        verificationTokenRetention = verificationTokenRetention == null ? Duration.ofDays(7) : verificationTokenRetention;
        activityLogRetention = activityLogRetention == null ? Duration.ofDays(90) : activityLogRetention;
        softDeletedFileRetention = softDeletedFileRetention == null ? Duration.ofDays(30) : softDeletedFileRetention;
    }
}
