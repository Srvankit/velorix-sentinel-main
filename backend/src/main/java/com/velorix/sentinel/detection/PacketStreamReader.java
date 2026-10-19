package com.velorix.sentinel.detection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Streams a run's frames out of PostgreSQL in capture order, one bounded chunk at a time.
 *
 * <p>Keyset pagination on {@code packet_number} rather than {@code OFFSET}: offsets make
 * the database re-scan everything it already returned, so a capture with a million frames
 * would get quadratically slower towards the end. The unique index on
 * {@code (analysis_id, packet_number)} makes each chunk an index range scan instead.</p>
 */
@Component
public class PacketStreamReader {

    private static final String SELECT_CHUNK = """
            SELECT packet_number, packet_timestamp, source_ip, destination_ip,
                   source_port, destination_port, protocol, network_protocol,
                   packet_length, payload_size, tcp_flags, is_malformed
            FROM packets
            WHERE analysis_id = ? AND packet_number > ?
            ORDER BY packet_number
            LIMIT ?
            """;

    private static final RowMapper<PacketRecord> ROW_MAPPER = PacketStreamReader::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public PacketStreamReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Feeds every frame of {@code analysisId} to {@code consumer} in capture order.
     *
     * @param chunkSize rows held in memory at once
     * @return number of frames streamed
     */
    public long stream(UUID analysisId, int chunkSize, Consumer<PacketRecord> consumer) {
        long cursor = 0L;
        long streamed = 0L;
        while (true) {
            List<PacketRecord> chunk = jdbcTemplate.query(
                    SELECT_CHUNK, ROW_MAPPER, analysisId, cursor, chunkSize);
            if (chunk.isEmpty()) {
                return streamed;
            }
            for (PacketRecord packet : chunk) {
                consumer.accept(packet);
            }
            streamed += chunk.size();
            cursor = chunk.get(chunk.size() - 1).number();
            if (chunk.size() < chunkSize) {
                return streamed;
            }
        }
    }

    private static PacketRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("packet_timestamp");
        return new PacketRecord(
                rs.getLong("packet_number"),
                timestamp == null ? null : timestamp.toInstant(),
                rs.getString("source_ip"),
                rs.getString("destination_ip"),
                nullableInt(rs, "source_port"),
                nullableInt(rs, "destination_port"),
                enumOrDefault(rs.getString("protocol")),
                networkOrDefault(rs.getString("network_protocol")),
                rs.getInt("packet_length"),
                rs.getInt("payload_size"),
                rs.getString("tcp_flags"),
                rs.getBoolean("is_malformed"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Protocol enumOrDefault(String raw) {
        try {
            return raw == null ? Protocol.OTHER : Protocol.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return Protocol.OTHER;
        }
    }

    private static NetworkProtocol networkOrDefault(String raw) {
        try {
            return raw == null ? NetworkProtocol.OTHER : NetworkProtocol.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return NetworkProtocol.OTHER;
        }
    }
}
