package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.Threat;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatStatus;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ThreatRepository extends JpaRepository<Threat, UUID>, JpaSpecificationExecutor<Threat> {

    /**
     * Owner-scoped threat feed. Every filter is optional; {@code null} means "any".
     *
     * <p>{@code term} is expected pre-lowered and wrapped in wildcards by the caller so the
     * comparison stays consistent across databases.</p>
     */
    @Query("""
            SELECT t FROM Threat t
            JOIN t.analysis a
            JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
              AND (:severity IS NULL OR t.severity = :severity)
              AND (:threatType IS NULL OR t.threatType = :threatType)
              AND (:status IS NULL OR t.status = :status)
              AND (:detectionRule IS NULL OR t.detectionRule = :detectionRule)
              AND (:minConfidence IS NULL OR t.confidenceScore >= :minConfidence)
              AND (:term IS NULL
                   OR LOWER(t.title) LIKE :term
                   OR LOWER(t.description) LIKE :term
                   OR LOWER(COALESCE(t.sourceIp, '')) LIKE :term
                   OR LOWER(COALESCE(t.destinationIp, '')) LIKE :term)
            """)
    Page<Threat> search(
            @Param("userId") UUID userId,
            @Param("analysisId") UUID analysisId,
            @Param("severity") ThreatSeverity severity,
            @Param("threatType") ThreatType threatType,
            @Param("status") ThreatStatus status,
            @Param("detectionRule") String detectionRule,
            @Param("minConfidence") Double minConfidence,
            @Param("term") String term,
            Pageable pageable);

    @Query("SELECT t FROM Threat t WHERE t.id = :id AND t.analysis.uploadedFile.user.id = :userId")
    Optional<Threat> findByIdAndOwner(@Param("id") UUID id, @Param("userId") UUID userId);

    /* ---------------------------------------------------------------- aggregates */

    @Query("""
            SELECT t.severity, COUNT(t) FROM Threat t
            JOIN t.analysis a JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
            GROUP BY t.severity
            """)
    List<Object[]> severityHistogram(@Param("userId") UUID userId, @Param("analysisId") UUID analysisId);

    @Query("""
            SELECT t.threatType, COUNT(t) FROM Threat t
            JOIN t.analysis a JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
            GROUP BY t.threatType
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> categoryHistogram(@Param("userId") UUID userId, @Param("analysisId") UUID analysisId);

    @Query("""
            SELECT t.sourceIp, COUNT(t) FROM Threat t
            JOIN t.analysis a JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
              AND t.sourceIp IS NOT NULL
            GROUP BY t.sourceIp
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> topSources(@Param("userId") UUID userId, @Param("analysisId") UUID analysisId, Pageable pageable);

    @Query("""
            SELECT t.destinationIp, COUNT(t) FROM Threat t
            JOIN t.analysis a JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
              AND t.destinationIp IS NOT NULL
            GROUP BY t.destinationIp
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> topDestinations(@Param("userId") UUID userId, @Param("analysisId") UUID analysisId,
                                   Pageable pageable);

    /** Detection timestamps and severities, used to build the activity timeline in memory. */
    @Query("""
            SELECT t.detectedAt, t.severity FROM Threat t
            JOIN t.analysis a JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:analysisId IS NULL OR a.id = :analysisId)
            ORDER BY t.detectedAt
            """)
    List<Object[]> detectionTimeline(@Param("userId") UUID userId, @Param("analysisId") UUID analysisId,
                                     Pageable pageable);

    long countByAnalysisId(UUID analysisId);

    long countByAnalysisIdAndSeverity(UUID analysisId, ThreatSeverity severity);

    void deleteByAnalysisId(UUID analysisId);

    @Query("""
            SELECT COUNT(t) FROM Threat t
            WHERE (:userId IS NULL OR t.analysis.uploadedFile.user.id = :userId)
              AND t.status IN :statuses
            """)
    long countByOwnerAndStatuses(@Param("userId") UUID userId,
                                 @Param("statuses") List<ThreatStatus> statuses);

}
