package com.velorix.sentinel.inspection;

import com.velorix.sentinel.inspection.decoder.DecodedPacket;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes decoded frames to the {@code packets} table in JDBC batches.
 *
 * <p>Deliberately bypasses JPA. Persisting millions of rows through the entity manager
 * would keep every row in the persistence context, flush one statement per row and run
 * out of heap long before a large capture finished. A batched prepared statement keeps
 * the write path flat in both memory and time.</p>
 */
@Component
public class PacketBatchWriter {

    private static final String INSERT_SQL = """
            INSERT INTO packets (
                id, analysis_id, packet_number, packet_timestamp,
                source_ip, destination_ip, source_port, destination_port,
                protocol, network_protocol, packet_length, captured_length, payload_size,
                ttl, tcp_flags, sequence_number, acknowledgement_number, window_size,
                checksum, source_mac, destination_mac, info,
                is_suspicious, is_malformed, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PacketBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts one batch of decoded frames.
     *
     * @param analysisId owning inspection run
     * @param batch      frames to persist; an empty batch is a no-op
     */
    public void write(UUID analysisId, List<DecodedPacket> batch) {
        if (batch.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (statement, packet) -> bind(
                statement, analysisId, packet, now));
    }

    private void bind(PreparedStatement statement, UUID analysisId, DecodedPacket packet, Timestamp now)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, UUID.randomUUID());
        statement.setObject(index++, analysisId);
        statement.setLong(index++, packet.number());
        statement.setTimestamp(index++, Timestamp.from(packet.timestamp()));
        statement.setString(index++, packet.sourceIp());
        statement.setString(index++, packet.destinationIp());
        setNullableInt(statement, index++, packet.sourcePort());
        setNullableInt(statement, index++, packet.destinationPort());
        statement.setString(index++, packet.protocol().name());
        statement.setString(index++, packet.networkProtocol().name());
        statement.setInt(index++, packet.packetLength());
        statement.setInt(index++, packet.capturedLength());
        statement.setInt(index++, packet.payloadSize());
        setNullableInt(statement, index++, packet.ttl());
        statement.setString(index++, packet.tcpFlags());
        setNullableLong(statement, index++, packet.sequenceNumber());
        setNullableLong(statement, index++, packet.acknowledgementNumber());
        setNullableInt(statement, index++, packet.windowSize());
        setNullableInt(statement, index++, packet.checksum());
        statement.setString(index++, packet.sourceMac());
        statement.setString(index++, packet.destinationMac());
        statement.setString(index++, packet.info());
        statement.setBoolean(index++, false);
        statement.setBoolean(index++, packet.malformed());
        statement.setTimestamp(index++, now);
        statement.setTimestamp(index++, now);
        statement.setLong(index, 0L);
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
