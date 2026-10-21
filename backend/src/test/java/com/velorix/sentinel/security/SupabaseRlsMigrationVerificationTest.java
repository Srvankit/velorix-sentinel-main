package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates the structure and safety of Flyway migration V7 (Supabase RLS & Role Hardening).
 *
 * Ensures:
 * 1. RLS is explicitly enabled on all 10 schema tables.
 * 2. Supabase API roles (anon, authenticated) have privileges conditionally revoked.
 * 3. Default privileges are hardened for future tables/sequences/routines.
 * 4. No permissive policies or backdoor grants are defined for anon or authenticated.
 * 5. No destructive operations (DROP TABLE, TRUNCATE) are present.
 * 6. Migration is strictly scoped to Task 2.1 (no unrelated Task 2.2 schema changes).
 */
class SupabaseRlsMigrationVerificationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V7__supabase_security_and_rls.sql");

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users",
            "refresh_tokens",
            "activity_logs",
            "verification_tokens",
            "uploaded_files",
            "analyses",
            "packets",
            "threats",
            "analysis_summaries",
            "detection_rules");

    @Test
    @DisplayName("Migration V7 exists and is readable")
    void migrationFileExists() {
        assertThat(Files.isRegularFile(MIGRATION_PATH))
                .as("V7 migration file must exist at " + MIGRATION_PATH)
                .isTrue();
    }

    @Test
    @DisplayName("Migration V7 enables Row Level Security on all 10 public application tables")
    void enablesRlsOnAllTables() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        Pattern rlsPattern = Pattern.compile(
                "ALTER\\s+TABLE\\s+([a-zA-Z0-9_]+)\\s+ENABLE\\s+ROW\\s+LEVEL\\s+SECURITY",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = rlsPattern.matcher(sql);

        Set<String> tablesWithRls = new java.util.HashSet<>();
        while (matcher.find()) {
            tablesWithRls.add(matcher.group(1).toLowerCase());
        }

        assertThat(tablesWithRls)
                .as("All 10 public tables must have RLS enabled in V7")
                .containsExactlyInAnyOrderElementsOf(REQUIRED_TABLES);
    }

    @Test
    @DisplayName("Migration V7 conditionally revokes privileges from anon and authenticated roles")
    void conditionallyRevokesPrivilegesFromSupabaseRoles() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        // Guarded role existence checks for compatibility with non-Supabase PostgreSQL environments
        assertThat(sql).containsIgnoringCase("IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')");
        assertThat(sql).containsIgnoringCase("IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated')");

        // Privilege revocations
        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;");
        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;");
        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon;");

        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated;");
        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated;");
        assertThat(sql).containsIgnoringCase("REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM authenticated;");

        // Default privileges revocation
        assertThat(sql).containsIgnoringCase("ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;");
        assertThat(sql).containsIgnoringCase("ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated;");
    }

    @Test
    @DisplayName("Migration V7 does not define permissive policies or grant access to PostgREST roles")
    void noPermissivePoliciesOrGrantsForApiRoles() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("Must not grant any permissions to anon or authenticated")
                .doesNotContainPattern("(?i)GRANT\\s+.*\\s+TO\\s+(anon|authenticated)");

        assertThat(sql)
                .as("Must not create permissive policies allowing bypass")
                .doesNotContainPattern("(?i)CREATE\\s+POLICY\\s+.*\\s+FOR\\s+ALL");
    }

    @Test
    @DisplayName("Migration V7 contains no destructive statements or unrelated Task 2.2 changes")
    void noDestructiveOperationsOrScopeCreep() throws IOException {
        String sql = Files.readString(MIGRATION_PATH);

        assertThat(sql)
                .as("Must not drop tables")
                .doesNotContainPattern("(?i)DROP\\s+TABLE");

        assertThat(sql)
                .as("Must not truncate tables")
                .doesNotContainPattern("(?i)TRUNCATE");

        assertThat(sql)
                .as("Must not implement Task 2.2 email index")
                .doesNotContainIgnoringCase("uk_users_lower_email");
    }
}
