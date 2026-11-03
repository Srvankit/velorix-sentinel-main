package com.velorix.sentinel.health;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.storage.FileStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator HealthIndicator verifying object storage availability.
 *
 * <p>Validates connectivity and access to local filesystem storage or S3-compatible cloud
 * storage without performing destructive operations, emitting unnecessary filesystem writes,
 * or exposing credentials.</p>
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(StorageHealthIndicator.class);
    private static final String PROBE_KEY = ".health-check-probe";

    private final FileStorageService storageService;
    private final StorageProperties properties;

    public StorageHealthIndicator(FileStorageService storageService, StorageProperties properties) {
        this.storageService = Objects.requireNonNull(storageService, "storageService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Health health() {
        String provider = properties.provider() != null ? properties.provider().toLowerCase() : "unknown";
        try {
            return switch (provider) {
                case "filesystem" -> checkFilesystemHealth();
                case "s3" -> checkS3Health();
                default -> Health.down()
                        .withDetail("provider", provider)
                        .withDetail("error", "Unrecognized storage provider: " + provider)
                        .build();
            };
        } catch (Exception ex) {
            log.warn("Storage health check failed for provider '{}': {}", provider, ex.getMessage());
            return Health.down(ex)
                    .withDetail("provider", provider)
                    .build();
        }
    }

    private Health checkFilesystemHealth() {
        String locationStr = properties.location();
        Path location = Path.of(locationStr != null ? locationStr : "./data/captures").toAbsolutePath().normalize();

        if (Files.exists(location) && Files.isDirectory(location) && Files.isReadable(location)) {
            return Health.up()
                    .withDetail("provider", "filesystem")
                    .withDetail("location", location.toString())
                    .withDetail("status", "AVAILABLE")
                    .build();
        }

        return Health.down()
                .withDetail("provider", "filesystem")
                .withDetail("location", location.toString())
                .withDetail("error", "Directory missing or inaccessible")
                .build();
    }

    private Health checkS3Health() {
        var s3Props = properties.s3();
        String bucket = s3Props != null ? s3Props.bucket() : "unknown";
        String region = s3Props != null ? s3Props.region() : "unknown";

        // Non-destructive probe: exists check does not create objects or mutate bucket state.
        // If S3 returns 200 or 404 for the probe key, connectivity to the bucket is verified.
        storageService.exists(PROBE_KEY);

        return Health.up()
                .withDetail("provider", "s3")
                .withDetail("bucket", bucket)
                .withDetail("region", region)
                .withDetail("status", "AVAILABLE")
                .build();
    }
}
