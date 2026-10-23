package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.service.impl.EmailLogSanitizer;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailLogSanitizerTest {

    @Test
    @DisplayName("Prefers opaque user ID when present")
    void prefersOpaqueUserId() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("ada.lovelace@velorix.io").build();

        assertThat(EmailLogSanitizer.describeRecipient(user)).isEqualTo("user " + id);
    }

    @Test
    @DisplayName("Masks email when user ID is null")
    void masksEmailWhenUserIdIsNull() {
        User user = User.builder().id(null).email("ada.lovelace@velorix.io").build();

        assertThat(EmailLogSanitizer.describeRecipient(user)).isEqualTo("a***e@velorix.io");
    }

    @Test
    @DisplayName("Masks emails of various lengths correctly")
    void masksEmailEdgeCases() {
        assertThat(EmailLogSanitizer.maskEmail("ada.lovelace@velorix.io")).isEqualTo("a***e@velorix.io");
        assertThat(EmailLogSanitizer.maskEmail("alan@velorix.io")).isEqualTo("a***n@velorix.io");
        assertThat(EmailLogSanitizer.maskEmail("me@domain.com")).isEqualTo("m***@domain.com");
        assertThat(EmailLogSanitizer.maskEmail("a@domain.com")).isEqualTo("a***@domain.com");
        assertThat(EmailLogSanitizer.maskEmail("   user@example.com  ")).isEqualTo("u***r@example.com");
        assertThat(EmailLogSanitizer.maskEmail(null)).isEqualTo("unknown");
        assertThat(EmailLogSanitizer.maskEmail("")).isEqualTo("unknown");
        assertThat(EmailLogSanitizer.maskEmail("   ")).isEqualTo("unknown");
        assertThat(EmailLogSanitizer.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(EmailLogSanitizer.maskEmail("@domain.com")).isEqualTo("***");
        assertThat(EmailLogSanitizer.maskEmail("user@")).isEqualTo("***");
    }

    @Test
    @DisplayName("Handles null user safely")
    void handlesNullUserSafely() {
        assertThat(EmailLogSanitizer.describeRecipient(null)).isEqualTo("unknown");
    }
}
