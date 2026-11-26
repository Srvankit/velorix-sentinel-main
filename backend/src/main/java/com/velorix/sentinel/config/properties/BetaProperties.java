package com.velorix.sentinel.config.properties;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Closed beta rollout configuration properties bound from {@code velorix.beta.*}.
 *
 * @param inviteOnly    whether registration is gated by an invite-only allowlist (default: false)
 * @param allowedEmails comma-separated list of invited email addresses or domain patterns (e.g. {@code @example.com})
 */
@ConfigurationProperties(prefix = "velorix.beta")
public record BetaProperties(
        Boolean inviteOnly,
        List<String> allowedEmails) {

    public BetaProperties {
        inviteOnly = inviteOnly != null && inviteOnly;
        allowedEmails = allowedEmails == null ? List.of() : allowedEmails;
    }

    /**
     * Evaluates whether the given email address is permitted to create an account.
     *
     * <p>When {@code inviteOnly} is false (the default), all valid email addresses are permitted.
     * When {@code inviteOnly} is true, the email must match an allowlist entry either by:
     * <ul>
     *   <li>Exact email match (case-insensitive, trimmed)</li>
     *   <li>Domain match if the pattern starts with {@code @} (e.g. {@code @example.com})</li>
     * </ul>
     * </p>
     *
     * @param email cleartext email address to evaluate
     * @return true if registration is allowed, false otherwise
     */
    public boolean isAllowed(String email) {
        if (!inviteOnly()) {
            return true;
        }
        if (email == null || email.isBlank()) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);

        for (String pattern : normalizedAllowedEntries()) {
            if (pattern.startsWith("@")) {
                if (cleanEmail.endsWith(pattern)) {
                    return true;
                }
            } else if (cleanEmail.equalsIgnoreCase(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of non-blank, trimmed, lowercased allowed entries.
     */
    public Set<String> normalizedAllowedEntries() {
        if (allowedEmails == null || allowedEmails.isEmpty()) {
            return Collections.emptySet();
        }
        return allowedEmails.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the number of distinct normalized entries in the allowlist.
     */
    public int allowedEntriesCount() {
        return normalizedAllowedEntries().size();
    }
}
