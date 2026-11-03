package com.velorix.sentinel.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.StorageException;
import com.velorix.sentinel.storage.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.util.unit.DataSize;

@DisplayName("Task 6.5 — StorageHealthIndicator Unit Test")
class StorageHealthIndicatorUnitTest {

    @Test
    @DisplayName("Filesystem storage returns UP when storage directory exists and is readable")
    void filesystemStorageReturnsUpWhenDirectoryExists(@TempDir Path tempDir) {
        FileStorageService storageService = mock(FileStorageService.class);
        StorageProperties properties = new StorageProperties(
                "filesystem",
                tempDir.toString(),
                DataSize.ofMegabytes(100),
                List.of("pcap"),
                List.of("application/vnd.tcpdump.pcap"),
                null,
                10,
                DataSize.ofMegabytes(500));

        StorageHealthIndicator indicator = new StorageHealthIndicator(storageService, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("provider", "filesystem");
        assertThat(health.getDetails()).containsEntry("status", "AVAILABLE");
        assertThat(health.getDetails().get("location").toString()).contains(tempDir.toString());
    }

    @Test
    @DisplayName("Filesystem storage returns DOWN when storage directory is missing")
    void filesystemStorageReturnsDownWhenDirectoryMissing() {
        FileStorageService storageService = mock(FileStorageService.class);
        Path nonexistent = Path.of(System.getProperty("java.io.tmpdir"), "velorix-nonexistent-" + System.currentTimeMillis());
        StorageProperties properties = new StorageProperties(
                "filesystem",
                nonexistent.toString(),
                DataSize.ofMegabytes(100),
                List.of("pcap"),
                List.of("application/vnd.tcpdump.pcap"),
                null,
                10,
                DataSize.ofMegabytes(500));

        StorageHealthIndicator indicator = new StorageHealthIndicator(storageService, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("provider", "filesystem");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("S3 storage returns UP when bucket is reachable")
    void s3StorageReturnsUpWhenBucketReachable() {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.exists(anyString())).thenReturn(false);

        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties(
                "my-sentinel-bucket",
                "us-west-2",
                "https://s3.us-west-2.amazonaws.com",
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                false,
                "captures/");

        StorageProperties properties = new StorageProperties(
                "s3",
                null,
                DataSize.ofMegabytes(100),
                List.of("pcap"),
                List.of("application/vnd.tcpdump.pcap"),
                s3,
                10,
                DataSize.ofMegabytes(500));

        StorageHealthIndicator indicator = new StorageHealthIndicator(storageService, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("provider", "s3");
        assertThat(health.getDetails()).containsEntry("bucket", "my-sentinel-bucket");
        assertThat(health.getDetails()).containsEntry("region", "us-west-2");
        assertThat(health.getDetails()).containsEntry("status", "AVAILABLE");

        // Sensitive credentials must NEVER be leaked in health details
        assertThat(health.getDetails()).doesNotContainKey("accessKey");
        assertThat(health.getDetails()).doesNotContainKey("secretKey");
        assertThat(health.getDetails().toString()).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(health.getDetails().toString()).doesNotContain("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
    }

    @Test
    @DisplayName("S3 storage returns DOWN when S3 connectivity fails with StorageException")
    void s3StorageReturnsDownWhenConnectivityFails() {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.exists(anyString())).thenThrow(new StorageException("S3 endpoint connection timed out"));

        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties(
                "offline-bucket",
                "eu-central-1",
                null,
                null,
                null,
                true,
                null);

        StorageProperties properties = new StorageProperties(
                "s3",
                null,
                DataSize.ofMegabytes(100),
                List.of("pcap"),
                List.of("application/vnd.tcpdump.pcap"),
                s3,
                10,
                DataSize.ofMegabytes(500));

        StorageHealthIndicator indicator = new StorageHealthIndicator(storageService, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("provider", "s3");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("Unknown storage provider returns DOWN with appropriate error detail")
    void unknownStorageProviderReturnsDown() {
        FileStorageService storageService = mock(FileStorageService.class);
        StorageProperties properties = new StorageProperties(
                "azure-blob-unsupported",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        StorageHealthIndicator indicator = new StorageHealthIndicator(storageService, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("provider", "azure-blob-unsupported");
    }
}
