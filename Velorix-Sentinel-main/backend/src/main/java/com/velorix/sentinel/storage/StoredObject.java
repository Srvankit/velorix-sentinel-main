package com.velorix.sentinel.storage;

/**
 * Result of a successful write to the binary store.
 *
 * @param storageKey opaque key the object can be retrieved with
 * @param sizeBytes  number of bytes actually written
 * @param checksum   lowercase hex SHA-256 of the written bytes
 */
public record StoredObject(String storageKey, long sizeBytes, String checksum) {
}
