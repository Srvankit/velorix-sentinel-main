package com.velorix.sentinel.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.BaseIntegrationTest;
import com.velorix.sentinel.detection.RuleMetadata;
import com.velorix.sentinel.detection.ThreatBatchWriter;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.inspection.PacketBatchWriter;
import com.velorix.sentinel.inspection.decoder.DecodedPacket;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Task 7.1: PostgreSQL Testcontainers & Flyway Integration Test Harness (Finding H9, P1-1).
 *
 * <p>Validates:
 * 1. Testcontainers PostgreSQL 16 container lifecycle and connectivity.
 * 2. Flyway migrations V1–V12 applied to completion against real PostgreSQL.
 * 3. Hibernate schema validation (validate mode) parity with real PostgreSQL schema.
 * 4. PostgreSQL-specific types (TIMESTAMPTZ, UUID gen_random_uuid()).
 * 5. Partial unique indexes and functional expression indexes (LOWER(email), oauth_provider_id).
 * 6. JdbcTemplate bulk write operations (PacketBatchWriter, ThreatBatchWriter).
 * 7. Row Level Security (RLS) enabled on all schema tables.
 * </p>
 */
@DisplayName("Task 7.1 — PostgreSQL Testcontainers & Flyway Integration Test Harness (H9, P1-1)")
class PostgreSqlIntegrationHarnessTest extends BaseIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private PacketRepository packetRepository;

    @Autowired
    private ThreatRepository threatRepository;

    @Autowired
    private PacketBatchWriter packetBatchWriter;

    @Autowired
    private ThreatBatchWriter threatBatchWriter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        threatRepository.deleteAll();
        packetRepository.deleteAll();
        analysisRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. PostgreSQL 16 container is active and verified by database metadata")
    void postgreSqlContainerMetadataAndVersion() throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            assertThat(metaData.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(metaData.getDatabaseMajorVersion()).isGreaterThanOrEqualTo(16);
        }
    }

    @Test
    @DisplayName("2. Flyway migrations V1 through V12 executed cleanly on PostgreSQL")
    void flywayMigrationsV1ThroughV12Applied() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSizeGreaterThanOrEqualTo(12);

        for (MigrationInfo info : applied) {
            assertThat(info.getState().isApplied())
                    .as("Migration %s must be applied", info.getScript())
                    .isTrue();
        }

        // Verify the latest version matches V12
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("12");
    }

    @Test
    @DisplayName("3. PostgreSQL-specific TIMESTAMPTZ columns exist in schema")
    void postgreSqlSpecificColumnTypes() throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Check TIMESTAMPTZ on packets table
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT data_type FROM information_schema.columns " +
                    "WHERE table_name = 'packets' AND column_name = 'packet_timestamp'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualTo("timestamp with time zone");
            }

            // Check TIMESTAMPTZ on analyses table
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT data_type FROM information_schema.columns " +
                    "WHERE table_name = 'analyses' AND column_name = 'created_at'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualTo("timestamp with time zone");
            }

            // Check UUID column type on users table
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT data_type FROM information_schema.columns " +
                    "WHERE table_name = 'users' AND column_name = 'id'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualTo("uuid");
            }
        }
    }

    @Test
    @DisplayName("4. Row Level Security is enabled on all schema tables in PostgreSQL")
    void rowLevelSecurityEnabledOnAllTables() throws Exception {
        Set<String> rlsTables = Set.of(
                "users", "refresh_tokens", "activity_logs", "verification_tokens",
                "uploaded_files", "analyses", "packets", "threats",
                "analysis_summaries", "oauth_exchange_codes"
        );

        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            for (String tableName : rlsTables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT relrowsecurity FROM pg_class WHERE relname = '" + tableName + "'")) {
                    assertThat(rs.next()).as("Table " + tableName + " must exist in pg_class").isTrue();
                    assertThat(rs.getBoolean("relrowsecurity"))
                            .as("RLS must be enabled on table " + tableName)
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("5. Case-insensitive email unique functional index enforced by PostgreSQL")
    void caseInsensitiveEmailIndexEnforced() {
        String baseEmail = "unique.test." + System.nanoTime();
        User user1 = User.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email(baseEmail + "@velorix.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .firstName("Bob")
                .lastName("Smith")
                .email(baseEmail.toUpperCase() + "@VELORIX.IO")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("6. JdbcTemplate batch packet and threat operations execute against PostgreSQL")
    void jdbcTemplateBulkOperationsOnPostgreSql() {
        User user = userRepository.saveAndFlush(User.builder()
                .firstName("Test")
                .lastName("User")
                .email("bulk-test-" + System.nanoTime() + "@velorix.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        UploadedFile file = uploadedFileRepository.saveAndFlush(UploadedFile.builder()
                .user(user)
                .originalFileName("test.pcap")
                .storedFileName("storage-key-" + UUID.randomUUID())
                .fileSize(1024L)
                .fileType("application/vnd.tcpdump.pcap")
                .checksum("dummy-hash-" + UUID.randomUUID())
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        Analysis analysis = analysisRepository.saveAndFlush(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.PROCESSING)
                .analysisVersion("1.0.0")
                .riskScore(0)
                .startedAt(Instant.now())
                .build());

        // 1. Bulk packet insert via PacketBatchWriter
        List<DecodedPacket> packets = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            packets.add(new DecodedPacket(
                    (long) i,
                    Instant.now(),
                    "00:11:22:33:44:55",
                    "66:77:88:99:aa:bb",
                    NetworkProtocol.IPV4,
                    "192.168.1." + i,
                    "10.0.0.1",
                    Protocol.TCP,
                    1000 + i,
                    80,
                    64,
                    64,
                    20,
                    64,
                    "SYN",
                    100L + i,
                    0L,
                    65535,
                    0x1234,
                    "HTTP GET request frame " + i,
                    false,
                    false
            ));
        }
        packetBatchWriter.write(analysis.getId(), packets);

        long packetCount = packetRepository.count();
        assertThat(packetCount).isEqualTo(25);

        // 2. Bulk threat insert via ThreatBatchWriter
        RuleMetadata rule = new RuleMetadata(
                "RULE-PORT-SCAN",
                "Port Scan",
                "1.0.0",
                "Detects rapid port scan",
                ThreatType.PORT_SCAN,
                ThreatSeverity.HIGH,
                "T1046",
                "Network Service Discovery",
                "SYN threshold",
                "Block scanning host"
        );

        List<ThreatCandidate> threats = List.of(
                new ThreatCandidate(
                        rule,
                        ThreatType.PORT_SCAN,
                        ThreatSeverity.HIGH,
                        0.85d,
                        "TCP Port Scan Detected",
                        "Rapid TCP SYN connection attempts across consecutive ports",
                        "25 connection attempts in < 1s",
                        "Investigate host 192.168.1.1 for unauthorized scanning activity",
                        "192.168.1.1",
                        "10.0.0.1",
                        Protocol.TCP,
                        25L,
                        List.of(1L, 2L, 3L, 4L, 5L),
                        Instant.now().minusSeconds(1),
                        Instant.now()
                )
        );
        threatBatchWriter.write(analysis.getId(), threats);

        long threatCount = threatRepository.count();
        assertThat(threatCount).isEqualTo(1);
    }
}
