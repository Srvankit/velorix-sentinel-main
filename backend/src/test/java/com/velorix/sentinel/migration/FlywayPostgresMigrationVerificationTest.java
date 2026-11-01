package com.velorix.sentinel.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlywayPostgresMigrationVerificationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5499/sentinel_test?options=-c%20timezone=UTC";
    private static final String USER = "postgres";
    private static final String PASS = "postgres";

    @BeforeAll
    static void checkPostgreSqlAvailability() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        boolean available = false;
        try (Socket socket = new Socket("localhost", 5499)) {
            available = true;
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "Local disposable PostgreSQL container on port 5499 is required for this verification test");
    }

    @Test
    @DisplayName("Verify V1 through V12 Flyway migrations against real PostgreSQL 16")
    void testFlywayMigrationsAgainstPostgreSql() throws Exception {
        // Clean schema if existing
        Flyway cleanFlyway = Flyway.configure()
                .dataSource(JDBC_URL, USER, PASS)
                .cleanDisabled(false)
                .load();
        cleanFlyway.clean();

        // 1. Run migrations V1 through V12 in an environment without Supabase roles
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, USER, PASS)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(12);

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 2. Verify table columns and types for oauth_exchange_codes (V11)
            Map<String, String> columns = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type, is_nullable " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = 'oauth_exchange_codes'")) {
                while (rs.next()) {
                    columns.put(rs.getString("column_name"), rs.getString("data_type"));
                }
            }

            assertThat(columns).containsEntry("id", "uuid");
            assertThat(columns).containsEntry("code_hash", "character varying");
            assertThat(columns).containsEntry("user_id", "uuid");
            assertThat(columns).containsEntry("expires_at", "timestamp with time zone");
            assertThat(columns).containsEntry("consumed_at", "timestamp with time zone");
            assertThat(columns).containsEntry("created_at", "timestamp with time zone");
            assertThat(columns).containsEntry("updated_at", "timestamp with time zone");
            assertThat(columns).containsEntry("version", "bigint");

            // 3. Verify Row Level Security (RLS) is enabled on oauth_exchange_codes
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE relname = 'oauth_exchange_codes'")) {
                assertThat(rs.next()).isTrue();
                boolean rlsEnabled = rs.getBoolean("relrowsecurity");
                assertThat(rlsEnabled).as("Row level security must be enabled").isTrue();
            }

            // 4. Verify indexes on oauth_exchange_codes
            Map<String, Boolean> indexes = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT indexname, indisunique " +
                    "FROM pg_indexes " +
                    "JOIN pg_class c ON c.relname = pg_indexes.indexname " +
                    "JOIN pg_index i ON i.indexrelid = c.oid " +
                    "WHERE tablename = 'oauth_exchange_codes'")) {
                while (rs.next()) {
                    indexes.put(rs.getString("indexname"), rs.getBoolean("indisunique"));
                }
            }

            assertThat(indexes).containsKey("uk_oauth_exchange_codes_hash");
            assertThat(indexes.get("uk_oauth_exchange_codes_hash")).isTrue();
            assertThat(indexes).containsKey("idx_oauth_exchange_codes_user");
            assertThat(indexes).containsKey("idx_oauth_exchange_codes_expires");

            // 5. Verify refresh_tokens table columns and types added in V12
            Map<String, String> refreshCols = new HashMap<>();
            Map<String, String> refreshNullable = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type, is_nullable " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = 'refresh_tokens'")) {
                while (rs.next()) {
                    refreshCols.put(rs.getString("column_name"), rs.getString("data_type"));
                    refreshNullable.put(rs.getString("column_name"), rs.getString("is_nullable"));
                }
            }

            assertThat(refreshCols).containsEntry("family_id", "uuid");
            assertThat(refreshNullable).containsEntry("family_id", "NO");
            assertThat(refreshCols).containsEntry("previous_token_hash", "character varying");

            // 6. Verify indexes on refresh_tokens added in V12
            Map<String, Boolean> refreshIndexes = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT indexname, indisunique " +
                    "FROM pg_indexes " +
                    "JOIN pg_class c ON c.relname = pg_indexes.indexname " +
                    "JOIN pg_index i ON i.indexrelid = c.oid " +
                    "WHERE tablename = 'refresh_tokens'")) {
                while (rs.next()) {
                    refreshIndexes.put(rs.getString("indexname"), rs.getBoolean("indisunique"));
                }
            }

            assertThat(refreshIndexes).containsKey("idx_refresh_tokens_family_id");
            assertThat(refreshIndexes).containsKey("idx_refresh_tokens_previous_hash");

            // 7. Test compatibility when Supabase roles 'anon' and 'authenticated' DO exist
            stmt.execute("DO $$ BEGIN " +
                         "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon; END IF; " +
                         "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN CREATE ROLE authenticated; END IF; " +
                         "END $$;");

            // Execute the V11 DO block again with the roles present
            stmt.execute("DO $$\n" +
                         "BEGIN\n" +
                         "    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN\n" +
                         "        REVOKE ALL ON TABLE oauth_exchange_codes FROM anon;\n" +
                         "    END IF;\n" +
                         "\n" +
                         "    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN\n" +
                         "        REVOKE ALL ON TABLE oauth_exchange_codes FROM authenticated;\n" +
                         "    END IF;\n" +
                         "END $$;");

            // Clean up test roles
            stmt.execute("DROP ROLE anon;");
            stmt.execute("DROP ROLE authenticated;");
        }
    }
}
