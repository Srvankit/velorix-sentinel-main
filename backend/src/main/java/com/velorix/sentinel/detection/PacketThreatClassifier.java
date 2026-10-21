package com.velorix.sentinel.detection;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Classifies packet records following threat detection, applying suspicious indicators
 * based strictly on detection evidence and computing safe and malicious packet counters.
 */
@Component
public class PacketThreatClassifier {

    private static final Logger log = LoggerFactory.getLogger(PacketThreatClassifier.class);
    private static final int BATCH_CHUNK_SIZE = 500;

    private static final String RESET_SQL = """
            UPDATE packets
            SET is_suspicious = FALSE
            WHERE analysis_id = ? AND is_suspicious = TRUE
            """;

    private static final String UPDATE_SUSPICIOUS_SQL = """
            UPDATE packets
            SET is_suspicious = TRUE
            WHERE analysis_id = ? AND packet_number = ?
            """;

    private static final String STATS_SQL = """
            SELECT
                COUNT(*) AS total_count,
                COUNT(CASE WHEN is_suspicious = TRUE THEN 1 END) AS malicious_count,
                COUNT(CASE WHEN is_suspicious = FALSE AND is_malformed = FALSE AND NOT (network_protocol = 'OTHER' AND protocol = 'OTHER') THEN 1 END) AS safe_count,
                COUNT(CASE WHEN is_suspicious = FALSE AND (is_malformed = TRUE OR (network_protocol = 'OTHER' AND protocol = 'OTHER')) THEN 1 END) AS unclassified_count
            FROM packets
            WHERE analysis_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PacketThreatClassifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Updates packets based on findings evidence and returns computed packet counters.
     *
     * @param analysisId the analysis being classified
     * @param findings   the collection of detection findings
     * @return accurate packet counters for safe, malicious, and unclassified packets
     */
    public PacketCounters classifyAndCount(UUID analysisId, List<ThreatCandidate> findings) {
        // 1. Reset previously flagged packets for this analysis (ensures idempotent re-runs)
        jdbcTemplate.update(RESET_SQL, analysisId);

        // 2. Aggregate all unique packet numbers associated with detection evidence.
        // A Set guarantees that multiple matching rules do not duplicate packet numbers.
        Set<Long> flaggedPacketNumbers = findings.stream()
                .flatMap(f -> f.samplePacketNumbers().stream())
                .collect(Collectors.toSet());

        // 3. Mark flagged packets as suspicious using JDBC batching
        if (!flaggedPacketNumbers.isEmpty()) {
            List<Long> numbers = List.copyOf(flaggedPacketNumbers);
            jdbcTemplate.batchUpdate(
                    UPDATE_SUSPICIOUS_SQL,
                    numbers,
                    BATCH_CHUNK_SIZE,
                    (ps, number) -> {
                        ps.setObject(1, analysisId);
                        ps.setLong(2, number);
                    });
            log.debug("Marked {} distinct packets as suspicious for analysis {}", numbers.size(), analysisId);
        }

        // 4. Calculate accurate counters directly from the packet records
        PacketCounters counters = jdbcTemplate.queryForObject(
                STATS_SQL,
                (rs, rowNum) -> new PacketCounters(
                        rs.getLong("total_count"),
                        rs.getLong("malicious_count"),
                        rs.getLong("safe_count"),
                        rs.getLong("unclassified_count")),
                analysisId);

        return counters != null ? counters : new PacketCounters(0L, 0L, 0L, 0L);
    }
}
