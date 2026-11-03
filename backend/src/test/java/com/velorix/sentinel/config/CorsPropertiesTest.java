package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Task 6.1 — Strict CORS Properties Unit Tests")
class CorsPropertiesTest {

    @Test
    @DisplayName("Default allowed origins contains only exact production origin and zero wildcards")
    void defaultAllowedOriginsContainsNoWildcards() {
        CorsProperties properties = new CorsProperties(null, null, null, null, null, null);

        assertThat(properties.allowedOrigins())
                .containsExactly("https://velorixsentinel.netlify.app");

        assertThat(properties.allowedOrigins().stream().noneMatch(o -> o.contains("*")))
                .isTrue();
    }

    @Test
    @DisplayName("normalizeOrigin canonicalizes valid HTTP and HTTPS origins")
    void normalizeOriginCanonicalizesValidOrigins() {
        assertThat(CorsProperties.normalizeOrigin("https://velorixsentinel.netlify.app"))
                .isEqualTo("https://velorixsentinel.netlify.app");

        assertThat(CorsProperties.normalizeOrigin("https://velorixsentinel.netlify.app/"))
                .isEqualTo("https://velorixsentinel.netlify.app");

        assertThat(CorsProperties.normalizeOrigin("https://velorixsentinel.netlify.app:443"))
                .isEqualTo("https://velorixsentinel.netlify.app");

        assertThat(CorsProperties.normalizeOrigin("http://localhost:8080"))
                .isEqualTo("http://localhost:8080");

        assertThat(CorsProperties.normalizeOrigin("http://localhost:80"))
                .isEqualTo("http://localhost");

        assertThat(CorsProperties.normalizeOrigin("HTTPS://VELORIXSENTINEL.NETLIFY.APP"))
                .isEqualTo("https://velorixsentinel.netlify.app");
    }

    @Test
    @DisplayName("normalizeOrigin strictly rejects wildcards, paths, queries, and invalid schemes")
    void normalizeOriginRejectsInvalidInputs() {
        assertThat(CorsProperties.normalizeOrigin("https://*.netlify.app")).isNull();
        assertThat(CorsProperties.normalizeOrigin("https://*.lovable.app")).isNull();
        assertThat(CorsProperties.normalizeOrigin("*")).isNull();
        assertThat(CorsProperties.normalizeOrigin("https://evil.com/some/path")).isNull();
        assertThat(CorsProperties.normalizeOrigin("https://evil.com?query=1")).isNull();
        assertThat(CorsProperties.normalizeOrigin("ftp://evil.com")).isNull();
        assertThat(CorsProperties.normalizeOrigin("javascript:alert(1)")).isNull();
        assertThat(CorsProperties.normalizeOrigin("")).isNull();
        assertThat(CorsProperties.normalizeOrigin("   ")).isNull();
        assertThat(CorsProperties.normalizeOrigin(null)).isNull();
    }

    @Test
    @DisplayName("Parses comma-separated origins and drops invalid/wildcard entries")
    void parsesCommaSeparatedOrigins() {
        CorsProperties properties = new CorsProperties(
                List.of("https://velorixsentinel.netlify.app, https://staging.velorix.com, https://*.netlify.app"),
                null, null, null, null, null
        );

        assertThat(properties.allowedOrigins())
                .containsExactlyInAnyOrder(
                        "https://velorixsentinel.netlify.app",
                        "https://staging.velorix.com"
                );
    }

    @Test
    @DisplayName("isAllowedOrigin performs exact, fully qualified matching")
    void isAllowedOriginExactMatching() {
        CorsProperties properties = new CorsProperties(
                List.of("https://velorixsentinel.netlify.app", "http://localhost:5173"),
                null, null, null, null, null
        );

        // Exact matches pass
        assertThat(properties.isAllowedOrigin("https://velorixsentinel.netlify.app")).isTrue();
        assertThat(properties.isAllowedOrigin("https://velorixsentinel.netlify.app/")).isTrue();
        assertThat(properties.isAllowedOrigin("https://velorixsentinel.netlify.app:443")).isTrue();
        assertThat(properties.isAllowedOrigin("http://localhost:5173")).isTrue();

        // Subdomain / multi-tenant attacks fail
        assertThat(properties.isAllowedOrigin("https://evil.netlify.app")).isFalse();
        assertThat(properties.isAllowedOrigin("https://attacker-velorixsentinel.netlify.app")).isFalse();
        assertThat(properties.isAllowedOrigin("https://velorixsentinel.netlify.app.evil.com")).isFalse();

        // Scheme and port mismatches fail
        assertThat(properties.isAllowedOrigin("http://velorixsentinel.netlify.app")).isFalse();
        assertThat(properties.isAllowedOrigin("https://velorixsentinel.netlify.app:8443")).isFalse();
        assertThat(properties.isAllowedOrigin("http://localhost:8080")).isFalse();

        // Null and empty fail
        assertThat(properties.isAllowedOrigin(null)).isFalse();
        assertThat(properties.isAllowedOrigin("")).isFalse();
        assertThat(properties.isAllowedOrigin("null")).isFalse();
    }
}

