package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.entity.User;

/**
 * Utility for sanitizing recipient metadata in operational email logs to prevent PII leakage.
 */
public final class EmailLogSanitizer {

    private EmailLogSanitizer() {}

    /**
     * Formats an opaque recipient reference. Prefers the opaque user ID; falls back to
     * a masked email address preserving only minimal troubleshooting metadata.
     */
    public static String describeRecipient(User user) {
        if (user != null && user.getId() != null) {
            return "user " + user.getId();
        }
        String email = user != null ? user.getEmail() : null;
        return maskEmail(email);
    }

    /**
     * Masks an email address so the full local part is not retained in cleartext logs.
     * Examples:
     * <ul>
     *   <li>{@code ada.lovelace@velorix.io} &rarr; {@code a***e@velorix.io}</li>
     *   <li>{@code me@velorix.io} &rarr; {@code m***@velorix.io}</li>
     *   <li>{@code a@velorix.io} &rarr; {@code a***@velorix.io}</li>
     * </ul>
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex == trimmed.length() - 1) {
            return "***";
        }
        String local = trimmed.substring(0, atIndex);
        String domain = trimmed.substring(atIndex + 1);
        String maskedLocal;
        if (local.length() <= 2) {
            maskedLocal = local.charAt(0) + "***";
        } else {
            maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        }
        return maskedLocal + "@" + domain;
    }
}
