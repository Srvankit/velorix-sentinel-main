package com.velorix.sentinel.config.properties;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Capture storage settings bound from {@code velorix.storage.*}.
 *
 * <p>The location is the root directory of the local filesystem backend. Swapping in an
 * S3 or Azure Blob backend only requires a different {@code FileStorageService} bean; the
 * limits below stay provider independent.</p>
 */
@ConfigurationProperties(prefix = "velorix.storage")
public record StorageProperties(
        String location,
        DataSize maxFileSize,
        List<String> allowedExtensions,
        List<String> allowedContentTypes) {

    private static final List<String> DEFAULT_EXTENSIONS = List.of("pcap", "pcapng");

    /**
     * Browsers and operating systems disagree wildly on the MIME type of a capture, so the
     * allow-list is deliberately broad and the extension plus magic-number checks are the
     * authoritative gate.
     */
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
        location = (location == null || location.isBlank()) ? "./data/captures" : location;
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(100) : maxFileSize;
        allowedExtensions = normalise(allowedExtensions, DEFAULT_EXTENSIONS);
        allowedContentTypes = normalise(allowedContentTypes, DEFAULT_CONTENT_TYPES);
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

    private static List<String> normalise(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.stream().map(value -> value.trim().toLowerCase()).toList();
    }
}
