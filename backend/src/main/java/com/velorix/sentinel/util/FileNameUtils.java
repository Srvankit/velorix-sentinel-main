package com.velorix.sentinel.util;

import java.util.Locale;
import java.util.UUID;

/**
 * Filename hygiene for everything that crosses the upload boundary.
 */
public final class FileNameUtils {

    private static final int MAX_NAME_LENGTH = 200;
    private static final String ILLEGAL_CHARACTERS = "[^A-Za-z0-9._-]";

    private FileNameUtils() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Strips any directory component and every character outside a conservative allow-list,
     * so a client supplied name can never influence the path an object is written to.
     */
    public static String sanitize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "capture";
        }
        String base = rawName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll(ILLEGAL_CHARACTERS, "_").replaceAll("_{2,}", "_");
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isBlank()) {
            return "capture";
        }
        return base.length() > MAX_NAME_LENGTH ? base.substring(0, MAX_NAME_LENGTH) : base;
    }

    /** Lowercase extension without the dot, or an empty string when there is none. */
    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Collision free, opaque storage key: no user input ever reaches the filesystem path. */
    public static String uniqueStorageKey(String extension) {
        String suffix = extension == null || extension.isBlank() ? "bin" : extension;
        return "%s.%s".formatted(UUID.randomUUID(), suffix);
    }
}
