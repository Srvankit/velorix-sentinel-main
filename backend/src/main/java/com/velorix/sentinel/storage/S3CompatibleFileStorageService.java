package com.velorix.sentinel.storage;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.PayloadTooLargeException;
import com.velorix.sentinel.exception.StorageException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible cloud object storage implementation of {@link FileStorageService}.
 *
 * <p>Compatible with AWS S3, Supabase Storage, Cloudflare R2, and MinIO.</p>
 *
 * <p>Persists captures in private cloud buckets, streaming uploads and downloads to
 * avoid unbounded memory consumption during packet inspection.</p>
 */
public class S3CompatibleFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleFileStorageService.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int STREAM_READ_BUFFER_SIZE = 64 * 1024; // 64 KB buffer for packet decoding

    private final StorageProperties properties;
    private final S3Client s3Client;

    public S3CompatibleFileStorageService(StorageProperties properties, S3Client s3Client) {
        this.properties = properties;
        this.s3Client = s3Client;
    }

    @Override
    public StoredObject store(String storageKey, InputStream content, long maxBytes) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("A storage key is required");
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(".s3-upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = copy(content, temp, digest, maxBytes);

            if (written == 0L) {
                throw new BadRequestException("The uploaded capture is empty");
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            String objectKey = resolveObjectKey(storageKey);
            String bucketName = properties.s3().bucket();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(resolveContentType(storageKey))
                    .contentLength(written)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(temp));
            log.info("Persisted capture to S3 bucket '{}' with key '{}' ({} bytes, sha256={})",
                    bucketName, objectKey, written, checksum);

            return new StoredObject(storageKey, written, checksum);
        } catch (NoSuchAlgorithmException ex) {
            throw new StorageException("SHA-256 is not available on this JVM", ex);
        } catch (IOException ex) {
            throw new StorageException("The capture could not be written to storage", ex);
        } catch (S3Exception ex) {
            log.error("Failed to upload object to S3 bucket '{}': {}", properties.s3().bucket(), ex.getMessage());
            throw new StorageException("The capture could not be written to object storage", ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("A storage key is required");
        }
        String objectKey = resolveObjectKey(storageKey);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            // Buffered stream (64KB) reduces HTTP round-trips when reading frame-by-frame
            return new BufferedInputStream(s3Stream, STREAM_READ_BUFFER_SIZE);
        } catch (NoSuchKeyException ex) {
            throw new StorageException("The capture is no longer present in storage");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new StorageException("The capture is no longer present in storage");
            }
            log.error("Failed to read object '{}' from S3: {}", objectKey, ex.getMessage());
            throw new StorageException("The capture could not be read from storage", ex);
        } catch (Exception ex) {
            throw new StorageException("The capture could not be read from storage", ex);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        if (!exists(storageKey)) {
            return false;
        }
        String objectKey = resolveObjectKey(storageKey);
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted capture object from S3: bucket='{}', key='{}'", properties.s3().bucket(), objectKey);
            return true;
        } catch (S3Exception ex) {
            log.error("Failed to delete object '{}' from S3: {}", objectKey, ex.getMessage());
            throw new StorageException("The capture could not be removed from storage", ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        String objectKey = resolveObjectKey(storageKey);
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            log.warn("Error checking existence of object '{}' in S3: {}", objectKey, ex.getMessage());
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private long copy(InputStream source, Path temp, MessageDigest digest, long maxBytes) throws IOException {
        long written = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream out = Files.newOutputStream(temp);
                DigestOutputStream digesting = new DigestOutputStream(out, digest)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                written += read;
                if (written > maxBytes) {
                    throw new PayloadTooLargeException(
                            "The capture exceeds the maximum allowed size of %d MB"
                                    .formatted(maxBytes / (1024 * 1024)));
                }
                digesting.write(buffer, 0, read);
            }
        }
        return written;
    }

    /**
     * Resolves the storage key to an S3 object key with optional prefix, rejecting directory traversal attempts.
     */
    private String resolveObjectKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("A storage key is required");
        }
        String cleanKey = storageKey.replace('\\', '/').trim();
        while (cleanKey.startsWith("/")) {
            cleanKey = cleanKey.substring(1);
        }
        if (cleanKey.contains("..")) {
            log.warn("Rejected storage key with path traversal: {}", storageKey);
            throw new StorageException("Illegal storage key");
        }
        String prefix = properties.s3().prefix();
        if (prefix == null || prefix.isBlank()) {
            return cleanKey;
        }
        return prefix + cleanKey;
    }

    private String resolveContentType(String storageKey) {
        String lower = storageKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pcapng")) {
            return "application/x-pcapng";
        }
        if (lower.endsWith(".pcap")) {
            return "application/vnd.tcpdump.pcap";
        }
        return "application/octet-stream";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Ignored on cleanup
        }
    }
}

