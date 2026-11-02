package com.velorix.sentinel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.PayloadTooLargeException;
import com.velorix.sentinel.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3CompatibleFileStorageServiceTest {

    private S3Client s3Client;
    private StorageProperties properties;
    private S3CompatibleFileStorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        StorageProperties.S3Properties s3Properties = new StorageProperties.S3Properties(
                "test-bucket",
                "us-east-1",
                "https://s3.us-east-1.amazonaws.com",
                "test-key",
                "test-secret",
                true,
                "captures/"
        );
        properties = new StorageProperties(
                "s3",
                "./data/captures",
                DataSize.ofMegabytes(100),
                List.of("pcap", "pcapng"),
                List.of("application/vnd.tcpdump.pcap"),
                s3Properties
        );
        storageService = new S3CompatibleFileStorageService(properties, s3Client);
    }

    @Test
    @DisplayName("Store: uploads capture to S3 with correct bucket, key, and SHA-256")
    void storeUploadsToS3Successfully() {
        byte[] payload = "test-pcap-binary-data".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StoredObject stored = storageService.store("session-01.pcap", new ByteArrayInputStream(payload), 1024 * 1024);

        assertThat(stored.storageKey()).isEqualTo("session-01.pcap");
        assertThat(stored.sizeBytes()).isEqualTo(payload.length);
        assertThat(stored.checksum()).isNotBlank();

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest putRequest = requestCaptor.getValue();
        assertThat(putRequest.bucket()).isEqualTo("test-bucket");
        assertThat(putRequest.key()).isEqualTo("captures/session-01.pcap");
        assertThat(putRequest.contentType()).isEqualTo("application/vnd.tcpdump.pcap");
        assertThat(putRequest.contentLength()).isEqualTo(payload.length);
    }

    @Test
    @DisplayName("Store: rejects empty capture with BadRequestException")
    void storeRejectsEmptyCapture() {
        byte[] emptyPayload = new byte[0];
        assertThatThrownBy(() -> storageService.store("empty.pcap", new ByteArrayInputStream(emptyPayload), 1024))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Store: rejects oversized capture exceeding maxBytes with PayloadTooLargeException")
    void storeRejectsOversizedCapture() {
        byte[] oversized = new byte[2048];
        assertThatThrownBy(() -> storageService.store("big.pcap", new ByteArrayInputStream(oversized), 1024))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }

    @Test
    @DisplayName("Store: rejects path traversal storage keys")
    void storeRejectsPathTraversalKeys() {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storageService.store("../malicious.pcap", new ByteArrayInputStream(payload), 1024))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Illegal storage key");
    }

    @Test
    @DisplayName("Open: returns buffered input stream from S3")
    void openReturnsStreamingContent() throws Exception {
        byte[] payload = "pcap-stream-content".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse getResponse = GetObjectResponse.builder().contentLength((long) payload.length).build();
        ResponseInputStream<GetObjectResponse> s3ResponseStream = new ResponseInputStream<>(
                getResponse,
                AbortableInputStream.create(new ByteArrayInputStream(payload))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3ResponseStream);

        try (InputStream stream = storageService.open("session-01.pcap")) {
            byte[] readBytes = stream.readAllBytes();
            assertThat(readBytes).isEqualTo(payload);
        }

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("captures/session-01.pcap");
    }

    @Test
    @DisplayName("Open: throws StorageException when object does not exist in S3")
    void openThrowsStorageExceptionOnMissingObject() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        assertThatThrownBy(() -> storageService.open("missing.pcap"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("no longer present in storage");
    }

    @Test
    @DisplayName("Exists: returns true when headObject succeeds, false when NoSuchKey")
    void existsReturnsAccurateStatus() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        assertThat(storageService.exists("session-01.pcap")).isTrue();

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());
        assertThat(storageService.exists("missing.pcap")).isFalse();
        assertThat(storageService.exists(null)).isFalse();
        assertThat(storageService.exists("")).isFalse();
    }

    @Test
    @DisplayName("Delete: removes object when exists, returns false when missing")
    void deleteRemovesObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        boolean deleted = storageService.delete("session-01.pcap");
        assertThat(deleted).isTrue();

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("captures/session-01.pcap");

        // When object does not exist
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        boolean deletedMissing = storageService.delete("missing.pcap");
        assertThat(deletedMissing).isFalse();
    }

    @Test
    @DisplayName("S3 error wraps in StorageException without leaking secrets")
    void s3ErrorWrapsInStorageException() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("Access Denied").build());

        assertThatThrownBy(() -> storageService.delete("error.pcap"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("could not be removed from storage");
    }
}

