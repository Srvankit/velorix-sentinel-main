package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.ThreatStatus;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes findings to the {@code threats} table in JDBC batches.
 *
 * <p>Mirrors {@code PacketBatchWriter}: a capture that trips a rule on ten thousand hosts
 * produces ten thousand rows, and pushing those through the entity manager would flush
 * one statement each.</p>
 */
@Component
public class ThreatBatchWriter {

    private static final String INSERT_SQL = """
            INSERT INTO threats (
                id, analysis_id, threat_type, severity, confidence_score,
                title, description, recommendation, detected_at,
                detection_rule, rule_version, evidence, mitre_technique, mitre_technique_name,
                source_ip, destination_ip, protocol, packet_count, sample_packet_numbers,
                first_seen_at, last_seen_at, status,
                created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final int MAX_TITLE = 160;
    private static final int MAX_TEXT = 2_000;
    private static final int MAX_EVIDENCE = 4_000;
    private static final int MAX_SAMPLES_TEXT = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public ThreatBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(UUID analysisId, List<ThreatCandidate> batch) {
        if (batch.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (statement, candidate) -> bind(
                statement, analysisId, candidate, now));
    }

    /** Removes previous findings so a re-run replaces rather than duplicates results. */
    public int deleteExisting(UUID analysisId) {
        return jdbcTemplate.update("DELETE FROM threats WHERE analysis_id = ?", analysisId);
    }

    private void bind(PreparedStatement statement, UUID analysisId, ThreatCandidate candidate, Timestamp now)
            throws SQLException {
        Instant detectedAt = candidate.detectedAt();
        int index = 1;
        statement.setObject(index++, UUID.randomUUID());
        statement.setObject(index++, analysisId);
        statement.setString(index++, candidate.threatType().name());
        statement.setString(index++, candidate.severity().name());
        statement.setDouble(index++, candidate.confidence());
        statement.setString(index++, truncate(candidate.title(), MAX_TITLE));
        statement.setString(index++, truncate(candidate.description(), MAX_TEXT));
        statement.setString(index++, truncate(candidate.recommendation(), MAX_TEXT));
        statement.setTimestamp(index++, Timestamp.from(detectedAt == null ? now.toInstant() : detectedAt));
        statement.setString(index++, candidate.rule().id());
        statement.setString(index++, candidate.rule().version());
        statement.setString(index++, truncate(candidate.evidence(), MAX_EVIDENCE));
        setNullableString(statement, index++, candidate.rule().mitreTechnique());
        setNullableString(statement, index++, candidate.rule().mitreName());
        setNullableString(statement, index++, candidate.sourceIp());
        setNullableString(statement, index++, candidate.destinationIp());
        statement.setString(index++, candidate.protocol().name());
        statement.setLong(index++, candidate.packetCount());
        setNullableString(statement, index++, truncate(joinSamples(candidate), MAX_SAMPLES_TEXT));
        setNullableTimestamp(statement, index++, candidate.firstSeenAt());
        setNullableTimestamp(statement, index++, candidate.lastSeenAt());
        statement.setString(index++, ThreatStatus.OPEN.name());
        statement.setTimestamp(index++, now);
        statement.setTimestamp(index++, now);
        statement.setLong(index, 0L);
    }

    private static String joinSamples(ThreatCandidate candidate) {
        if (candidate.samplePacketNumbers().isEmpty()) {
            return null;
        }
        return candidate.samplePacketNumbers().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableTimestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }
}
