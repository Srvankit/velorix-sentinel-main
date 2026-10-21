package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Static invariant verification test validating the structure, DDL statements,
 * and safety invariants of Flyway migration script V8 (Case-Insensitive Email Index & Schema Cleanliness).
 *
 * <p>Note: This test statically verifies migration file contents and syntax invariants.
 * It does not execute the DDL against a live PostgreSQL database.
 */
class CaseInsensitiveEmailMigrationVerificationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V8__case_insensitive_email_and_schema_cleanliness.sql");

    @Test
    @DisplayName("Migration V8 exists and is readable")
    void migrationFileExists() {
        assertThat(Files.isRegularFile(MIGRATION_PATH))
                .as("V8 migration file must exist at " + MIGRATION_PATH)
                .isTrue();
    }

    @Test
    @DisplayName("Migration V8 creates the unique functional index on LOWER(email)")
    void createsUniqueLowerEmailIndex() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("V8 must create uk_users_lower_email functional index on LOWER(email)")
                .containsPattern("(?i)CREATE\\s+UNIQUE\\s+INDEX\\s+(IF\\s+NOT\\s+EXISTS\\s+)?uk_users_lower_email\\s+ON\\s+users\\s*\\(\\s*LOWER\\s*\\(\\s*email\\s*\\)\\s*\\)");
    }

    @Test
    @DisplayName("Migration V8 drops the unmapped detection_rules table safely without CASCADE")
    void dropsUnmappedDetectionRulesTableSafelyWithoutCascade() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("V8 must drop the unused detection_rules table")
                .containsPattern("(?i)DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?detection_rules\\s*;");

        assertThat(sql)
                .as("V8 must NOT use CASCADE on DROP TABLE to ensure fail-safe behavior if dependent objects exist")
                .doesNotContainPattern("(?i)CASCADE");
    }

    @Test
    @DisplayName("Migration V8 contains no destructive operations on users or data tables")
    void noDestructiveOperationsOnDataTables() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("Must not drop users or other application data tables")
                .doesNotContainPattern("(?i)DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?(users|uploaded_files|analyses|packets|threats)");

        assertThat(sql)
                .as("Must not truncate tables")
                .doesNotContainPattern("(?i)TRUNCATE");
    }
}
