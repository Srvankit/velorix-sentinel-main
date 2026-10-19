package com.velorix.sentinel.storage;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.PayloadTooLargeException;
import com.velorix.sentinel.exception.StorageException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local filesystem implementation of the capture store.
 *
 * <p>Writes go to a temporary file first and are atomically moved into place, so a failed
 * or oversized upload can never leave a half written capture behind. Every resolved path
 * is verified to stay inside the configured root, which makes path traversal impossible
 * even if a malicious key ever reached this layer.</p>
 */
@Service
public class LocalFileSystemStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorageService.class);
    private static final int BUFFER_SIZE = 8192;

    private final StorageProperties properties;
    private Path root;

    public LocalFileSystemStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialise() {
        try {
            root = Path.of(properties.location()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("Capture storage initialised at {}", root);
        } catch (IOException ex) {
            throw new StorageException("Capture storage location could not be created", ex);
        }
    }

    @Override
    public StoredObject store(String storageKey, InputStream content, long maxBytes) {
        Path target = resolve(storageKey);
        Path temp = null;
        try {
            temp = Files.createTempFile(root, ".upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = copy(content, temp, digest, maxBytes);

            if (written == 0L) {
                throw new com.velorix.sentinel.exception.BadRequestException("The uploaded capture is empty");
            }

            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            return new StoredObject(storageKey, written, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new StorageException("SHA-256 is not available on this JVM", ex);
        } catch (IOException ex) {
            throw new StorageException("The capture could not be written to storage", ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path source = resolve(storageKey);
        if (!Files.isRegularFile(source)) {
            throw new StorageException("The capture is no longer present in storage");
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException ex) {
            throw new StorageException("The capture could not be read from storage", ex);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new StorageException("The capture could not be removed from storage", ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
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

    /** Resolves a key inside the storage root, rejecting anything that escapes it. */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("A storage key is required");
        }
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root)) {
            log.warn("Rejected storage key escaping the storage root: {}", storageKey);
            throw new StorageException("Illegal storage key");
        }
        return candidate;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Temporary upload file {} could not be removed: {}", path, ex.getMessage());
        }
    }
}
