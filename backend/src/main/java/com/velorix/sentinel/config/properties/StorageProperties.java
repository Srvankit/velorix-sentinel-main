package com.velorix.sentinel.config.properties;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Capture storage settings bound from {@code velorix.storage.*}.
 *
 * <p>Supports local filesystem backend and S3-compatible cloud object storage
 * (AWS S3, Supabase Storage, Cloudflare R2, MinIO).</p>
 */
@ConfigurationProperties(prefix = "velorix.storage")
public record StorageProperties(
        String provider,
        String location,
        DataSize maxFileSize,
        List<String> allowedExtensions,
        List<String> allowedContentTypes,
        S3Properties s3,
        Integer maxFilesPerUser,
        DataSize maxStoragePerUser) {

    private static final List<String> DEFAULT_EXTENSIONS = List.of("pcap", "pcapng");

    private static final List<String> DEFAULT_CONTENT_TYPES = List.of(
            "application/vnd.tcpdump.pcap",
            "application/x-pcapng",
            "application/x-pcap",
            "application/pcap",
            "application/cap",
            "application/octet-stream",
            "application/x-x509-ca-cert",
            "");

    public StorageProperties {
        provider = (provider == null || provider.isBlank()) ? "filesystem" : provider.trim().toLowerCase();
        location = (location == null || location.isBlank()) ? "./data/captures" : location;
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(100) : maxFileSize;
        allowedExtensions = normalise(allowedExtensions, DEFAULT_EXTENSIONS);
        allowedContentTypes = normalise(allowedContentTypes, DEFAULT_CONTENT_TYPES);
        s3 = s3 == null ? new S3Properties(null, null, null, null, null, true, null) : s3;
        maxFilesPerUser = (maxFilesPerUser == null || maxFilesPerUser <= 0) ? 10 : maxFilesPerUser;
        maxStoragePerUser = maxStoragePerUser == null ? DataSize.ofMegabytes(500) : maxStoragePerUser;
    }

    public Set<String> extensions() {
        return Set.copyOf(allowedExtensions);
    }

    public Set<String> contentTypes() {
        return Set.copyOf(allowedContentTypes);
    }

    public long maxFileSizeBytes() {
        return maxFileSize.toBytes();
    }

    public long maxStoragePerUserBytes() {
        return maxStoragePerUser.toBytes();
    }

    private static List<String> normalise(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.stream().map(value -> value.trim().toLowerCase()).toList();
    }

    /**
     * S3 and S3-compatible (Supabase Storage, Cloudflare R2, MinIO) configuration settings.
     */
    public record S3Properties(
            String bucket,
            String region,
            String endpoint,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            String prefix) {

        public S3Properties {
            bucket = (bucket == null || bucket.isBlank()) ? "velorix-captures" : bucket.trim();
            region = (region == null || region.isBlank()) ? "us-east-1" : region.trim();
            endpoint = (endpoint == null || endpoint.isBlank()) ? null : endpoint.trim();
            accessKey = (accessKey == null || accessKey.isBlank()) ? null : accessKey.trim();
            secretKey = (secretKey == null || secretKey.isBlank()) ? null : secretKey.trim();
            prefix = (prefix == null || prefix.isBlank()) ? "captures/" : (prefix.endsWith("/") ? prefix : prefix + "/");
        }
    }
}
