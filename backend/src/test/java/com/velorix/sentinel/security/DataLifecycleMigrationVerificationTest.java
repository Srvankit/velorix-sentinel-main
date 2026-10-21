package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Static invariant verification test validating the structure, DDL statements,
 * and safety invariants of Flyway migration script V9 (Data Lifecycle & Packet Index Optimization).
 *
 * <p>Note: This test statically verifies migration file contents and syntax invariants.
 * It does not execute the DDL against a live PostgreSQL database.
 */
class DataLifecycleMigrationVerificationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V9__data_lifecycle_and_index_optimization.sql");

    @Test
    @DisplayName("Migration V9 exists and is readable")
    void migrationFileExists() {
        assertThat(Files.isRegularFile(MIGRATION_PATH))
                .as("V9 migration file must exist at " + MIGRATION_PATH)
                .isTrue();
    }

    @Test
    @DisplayName("Migration V9 drops only the standalone duplicate index idx_packets_analysis_number")
    void dropsStandaloneDuplicateIndex() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("V9 must drop the redundant idx_packets_analysis_number index")
                .containsPattern("(?i)DROP\\s+INDEX\\s+(IF\\s+EXISTS\\s+)?idx_packets_analysis_number\\s*;");
    }

    @Test
    @DisplayName("Migration V9 preserves idx_packets_analysis and required query indexes")
    void preservesPrefixAndQueryIndexes() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("V9 must not drop idx_packets_analysis without profiling evidence")
                .doesNotContainPattern("(?i)DROP\\s+INDEX\\s+(IF\\s+EXISTS\\s+)?idx_packets_analysis\\s*;");

        assertThat(sql)
                .as("V9 must not drop uk_packets_analysis_number")
                .doesNotContainPattern("(?i)DROP\\s+(INDEX|CONSTRAINT)\\s+.*uk_packets_analysis_number");
    }

    @Test
    @DisplayName("Migration V9 contains no destructive operations on tables or constraints")
    void noDestructiveOperationsOnTablesOrConstraints() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("Must not drop tables")
                .doesNotContainPattern("(?i)DROP\\s+TABLE");

        assertThat(sql)
                .as("Must not drop constraints")
                .doesNotContainPattern("(?i)DROP\\s+CONSTRAINT");

        assertThat(sql)
                .as("Must not truncate tables")
                .doesNotContainPattern("(?i)TRUNCATE");
    }
}
