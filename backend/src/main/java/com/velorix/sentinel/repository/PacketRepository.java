package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.Packet;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.repository.projection.ConversationRow;
import com.velorix.sentinel.repository.projection.EndpointCount;
import com.velorix.sentinel.repository.projection.PortCount;
import com.velorix.sentinel.repository.projection.ProtocolCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PacketRepository extends JpaRepository<Packet, UUID>, JpaSpecificationExecutor<Packet> {

    /**
     * Paginated, filtered frame listing for one run.
     *
     * <p>{@code term} matches either endpoint address; it is lower-cased by the caller so
     * the comparison stays index friendly and case insensitive.</p>
     */
    @Query("""
            SELECT p FROM Packet p
            WHERE p.analysis.id = :analysisId
              AND (:protocol IS NULL OR p.protocol = :protocol)
              AND (:networkProtocol IS NULL OR p.networkProtocol = :networkProtocol)
              AND (:suspicious IS NULL OR p.suspicious = :suspicious)
              AND (:term IS NULL
                   OR LOWER(p.sourceIp) LIKE :term
                   OR LOWER(p.destinationIp) LIKE :term
                   OR LOWER(COALESCE(p.info, '')) LIKE :term)
            """)
    Page<Packet> search(
            @Param("analysisId") UUID analysisId,
            @Param("protocol") Protocol protocol,
            @Param("networkProtocol") NetworkProtocol networkProtocol,
            @Param("suspicious") Boolean suspicious,
            @Param("term") String term,
            Pageable pageable);

    Optional<Packet> findByIdAndAnalysisId(UUID id, UUID analysisId);

    /** Protocol histogram for one run, ordered by frequency. */
    @Query("""
            SELECT p.protocol AS protocol, COUNT(p) AS total
            FROM Packet p
            WHERE p.analysis.id = :analysisId
            GROUP BY p.protocol
            ORDER BY COUNT(p) DESC
            """)
    List<ProtocolCount> protocolDistribution(@Param("analysisId") UUID analysisId);

    @Query("SELECT COUNT(DISTINCT p.sourceIp) FROM Packet p WHERE p.analysis.id = :analysisId")
    long countDistinctSourceIps(@Param("analysisId") UUID analysisId);

    @Query("SELECT COUNT(DISTINCT p.destinationIp) FROM Packet p WHERE p.analysis.id = :analysisId")
    long countDistinctDestinationIps(@Param("analysisId") UUID analysisId);

    long countByAnalysisId(UUID analysisId);

    long countByAnalysisIdAndSuspiciousTrue(UUID analysisId);

    /** Bulk delete used when a run is restarted; avoids loading rows into the context. */
    @Modifying
    @Query("DELETE FROM Packet p WHERE p.analysis.id = :analysisId")
    int deleteByAnalysisId(@Param("analysisId") UUID analysisId);

    /** Protocol histogram across every run in the owner scope. */
    @Query("""
            SELECT p.protocol AS protocol, COUNT(p) AS total
            FROM Packet p
            WHERE (:userId IS NULL OR p.analysis.uploadedFile.user.id = :userId)
            GROUP BY p.protocol
            ORDER BY COUNT(p) DESC
            """)
    List<ProtocolCount> protocolDistributionForOwner(@Param("userId") UUID userId);

    /** Total wire bytes of one run; 0 when the run has no frames yet. */
    @Query("SELECT COALESCE(SUM(p.packetLength), 0) FROM Packet p WHERE p.analysis.id = :analysisId")
    long totalBytes(@Param("analysisId") UUID analysisId);

    /** Ranked source addresses. The caller supplies the limit through {@link Pageable}. */
    @Query("""
            SELECT p.sourceIp AS label, COUNT(p) AS total, COALESCE(SUM(p.packetLength), 0) AS bytes
            FROM Packet p
            WHERE p.analysis.id = :analysisId
            GROUP BY p.sourceIp
            ORDER BY COUNT(p) DESC
            """)
    List<EndpointCount> topSourceIps(@Param("analysisId") UUID analysisId, Pageable pageable);

    /** Ranked destination addresses. */
    @Query("""
            SELECT p.destinationIp AS label, COUNT(p) AS total, COALESCE(SUM(p.packetLength), 0) AS bytes
            FROM Packet p
            WHERE p.analysis.id = :analysisId
            GROUP BY p.destinationIp
            ORDER BY COUNT(p) DESC
            """)
    List<EndpointCount> topDestinationIps(@Param("analysisId") UUID analysisId, Pageable pageable);

    /** Ranked server side ports, i.e. the destination port of each frame that carries one. */
    @Query("""
            SELECT p.destinationPort AS port, COUNT(p) AS total, COALESCE(SUM(p.packetLength), 0) AS bytes
            FROM Packet p
            WHERE p.analysis.id = :analysisId AND p.destinationPort IS NOT NULL
            GROUP BY p.destinationPort
            ORDER BY COUNT(p) DESC
            """)
    List<PortCount> topDestinationPorts(@Param("analysisId") UUID analysisId, Pageable pageable);

    /** Distinct addresses seen on either side of the wire. */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT source_ip AS ip FROM packets WHERE analysis_id = :analysisId
                UNION
                SELECT destination_ip FROM packets WHERE analysis_id = :analysisId
            ) addresses
            """, nativeQuery = true)
    long countDistinctAddresses(@Param("analysisId") UUID analysisId);

    /** Distinct transport ports seen on either side of the wire. */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT source_port AS port FROM packets
                WHERE analysis_id = :analysisId AND source_port IS NOT NULL
                UNION
                SELECT destination_port FROM packets
                WHERE analysis_id = :analysisId AND destination_port IS NOT NULL
            ) ports
            """, nativeQuery = true)
    long countDistinctPorts(@Param("analysisId") UUID analysisId);

    /**
     * Frames folded into conversations: one row per (source, destination, protocol) triple.
     *
     * <p>Native because the count query needs a derived table, which JPQL cannot express.
     * Aliases are quoted so PostgreSQL preserves the camel case the projection binds to.</p>
     */
    @Query(value = """
            SELECT p.source_ip AS "clientIp",
                   p.destination_ip AS "serverIp",
                   p.protocol AS "protocol",
                   MIN(p.destination_port) AS "serverPort",
                   COUNT(*) AS "packets",
                   COALESCE(SUM(p.packet_length), 0) AS "bytes",
                   COALESCE(SUM(CASE WHEN p.is_suspicious THEN 1 ELSE 0 END), 0) AS "suspiciousPackets",
                   EXTRACT(EPOCH FROM MIN(p.packet_timestamp)) * 1000 AS "startedAtMs",
                   EXTRACT(EPOCH FROM MAX(p.packet_timestamp)) * 1000 AS "endedAtMs"
            FROM packets p
            WHERE p.analysis_id = :analysisId
            GROUP BY p.source_ip, p.destination_ip, p.protocol
            """,
            countQuery = """
                    SELECT COUNT(*) FROM (
                        SELECT 1 FROM packets p
                        WHERE p.analysis_id = :analysisId
                        GROUP BY p.source_ip, p.destination_ip, p.protocol
                    ) conversations
                    """,
            nativeQuery = true)
    Page<ConversationRow> conversations(@Param("analysisId") UUID analysisId, Pageable pageable);

}
