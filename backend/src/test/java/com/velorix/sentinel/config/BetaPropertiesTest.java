package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.BetaProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Task 8.4 — BetaProperties Unit Tests")
class BetaPropertiesTest {

    @Test
    @DisplayName("When inviteOnly is false, any email is permitted")
    void whenInviteOnlyIsFalseAnyEmailIsPermitted() {
        BetaProperties props = new BetaProperties(false, List.of("allowed@example.com"));
        assertThat(props.isAllowed("random@other.com")).isTrue();
        assertThat(props.isAllowed("allowed@example.com")).isTrue();
        assertThat(props.isAllowed("")).isTrue();
        assertThat(props.isAllowed(null)).isTrue();
    }

    @Test
    @DisplayName("When inviteOnly is null, it defaults to false (open registration)")
    void whenInviteOnlyIsNullItDefaultsToFalse() {
        BetaProperties props = new BetaProperties(null, List.of());
        assertThat(props.isAllowed("random@other.com")).isTrue();
        assertThat(props.allowedEntriesCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Exact email matches case-insensitively when inviteOnly is true")
    void exactEmailMatchesCaseInsensitive() {
        BetaProperties props = new BetaProperties(true, List.of("BetaTester@Velorix.Tech"));
        assertThat(props.isAllowed("betatester@velorix.tech")).isTrue();
        assertThat(props.isAllowed("BETATESTER@VELORIX.TECH")).isTrue();
        assertThat(props.isAllowed("BetaTester@Velorix.Tech")).isTrue();
        assertThat(props.isAllowed("other@velorix.tech")).isFalse();
    }

    @Test
    @DisplayName("Domain entry matches all emails on that domain")
    void domainEntryMatchesAllEmailsOnDomain() {
        BetaProperties props = new BetaProperties(true, List.of("@velorix.tech", "partner@external.org"));
        assertThat(props.isAllowed("alice@velorix.tech")).isTrue();
        assertThat(props.isAllowed("bob@VELORIX.TECH")).isTrue();
        assertThat(props.isAllowed("partner@external.org")).isTrue();
        assertThat(props.isAllowed("intruder@external.org")).isFalse();
        assertThat(props.isAllowed("mallory@fakevelorix.tech")).isFalse();
    }

    @Test
    @DisplayName("Whitespace and empty entries are trimmed and filtered")
    void whitespaceAndEmptyEntriesAreTrimmedAndFiltered() {
        BetaProperties props = new BetaProperties(true, List.of("  tester1@example.com  ", "   ", "@alpha.io  "));
        assertThat(props.allowedEntriesCount()).isEqualTo(2);
        assertThat(props.isAllowed("tester1@example.com")).isTrue();
        assertThat(props.isAllowed("admin@alpha.io")).isTrue();
        assertThat(props.isAllowed("tester2@example.com")).isFalse();
    }

    @Test
    @DisplayName("Null or blank email check returns false when inviteOnly is true")
    void nullOrBlankEmailCheckReturnsFalseWhenInviteOnlyIsTrue() {
        BetaProperties props = new BetaProperties(true, List.of("tester1@example.com"));
        assertThat(props.isAllowed(null)).isFalse();
        assertThat(props.isAllowed("")).isFalse();
        assertThat(props.isAllowed("   ")).isFalse();
    }

    @Test
    @DisplayName("When inviteOnly is true and allowed-emails is empty, all emails are rejected")
    void whenInviteOnlyIsTrueAndEmptyAllRejected() {
        BetaProperties props = new BetaProperties(true, List.of());
        assertThat(props.allowedEntriesCount()).isEqualTo(0);
        assertThat(props.isAllowed("tester@example.com")).isFalse();
    }
}
