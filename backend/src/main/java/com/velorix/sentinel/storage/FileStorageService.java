package com.velorix.sentinel.storage;

import java.io.InputStream;

/**
 * Provider-agnostic binary store for uploaded captures.
 *
 * <p>Callers only ever see opaque storage keys - never a filesystem path, bucket name or
 * URL - so the local filesystem implementation can be replaced by AWS S3 or Azure Blob
 * Storage without touching the service layer.</p>
 */
public interface FileStorageService {

    /**
     * Persists the stream under the supplied opaque key.
     *
     * @param storageKey unique, already sanitised key
     * @param content    stream of bytes; the caller owns closing the source
     * @param maxBytes   hard limit; exceeding it aborts the write and removes partial data
     * @return the number of bytes written together with the SHA-256 checksum
     */
    StoredObject store(String storageKey, InputStream content, long maxBytes);

    /**
     * Opens the stored object for sequential reading.
     *
     * <p>The stream is unbuffered and must be closed by the caller. Deliberately a stream
     * and not a byte array: captures are up to 100 MB and are always processed frame by
     * frame, never materialised whole.</p>
     *
     * @throws com.velorix.sentinel.exception.StorageException when the object is missing
     *                                                          or unreadable
     */
    InputStream open(String storageKey);

    /** Removes the object. Returns {@code true} when something was actually deleted. */
    boolean delete(String storageKey);

    boolean exists(String storageKey);
}
