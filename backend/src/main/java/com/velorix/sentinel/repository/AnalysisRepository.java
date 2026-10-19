package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID>, JpaSpecificationExecutor<Analysis> {

    /**
     * Owner-scoped, optionally filtered listing. A {@code null} filter means "any".
     */
    @EntityGraph(attributePaths = "uploadedFile")
    @Query("""
            SELECT a FROM Analysis a
            JOIN a.uploadedFile f
            WHERE (:userId IS NULL OR f.user.id = :userId)
              AND (:status IS NULL OR a.status = :status)
              AND (:uploadedFileId IS NULL OR f.id = :uploadedFileId)
            """)
    Page<Analysis> search(
            @Param("userId") UUID userId,
            @Param("status") AnalysisStatus status,
            @Param("uploadedFileId") UUID uploadedFileId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"uploadedFile", "summary"})
    Optional<Analysis> findWithDetailsById(UUID id);

    @Query("SELECT a FROM Analysis a WHERE a.id = :id AND a.uploadedFile.user.id = :userId")
    Optional<Analysis> findByIdAndOwner(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT COUNT(a) FROM Analysis a WHERE a.uploadedFile.user.id = :userId")
    long countByOwner(@Param("userId") UUID userId);

    Page<Analysis> findAllByUploadedFileId(UUID uploadedFileId, Pageable pageable);

    /** True when a run over this capture is queued or already inspecting. */
    @Query("""
            SELECT COUNT(a) > 0 FROM Analysis a
            WHERE a.uploadedFile.id = :uploadedFileId
              AND a.status IN (com.velorix.sentinel.entity.enums.AnalysisStatus.QUEUED,
                               com.velorix.sentinel.entity.enums.AnalysisStatus.PROCESSING)
            """)
    boolean existsActiveForFile(@Param("uploadedFileId") UUID uploadedFileId);

    /* ------------------------------------------------------- dashboard aggregates */

    @Query("SELECT COUNT(a) FROM Analysis a WHERE (:userId IS NULL OR a.uploadedFile.user.id = :userId)")
    long countForOwner(@Param("userId") UUID userId);

    @Query("""
            SELECT a.status, COUNT(a) FROM Analysis a
            WHERE (:userId IS NULL OR a.uploadedFile.user.id = :userId)
            GROUP BY a.status
            """)
    List<Object[]> statusHistogram(@Param("userId") UUID userId);

    @Query("""
            SELECT COALESCE(SUM(a.totalPackets), 0) FROM Analysis a
            WHERE (:userId IS NULL OR a.uploadedFile.user.id = :userId)
            """)
    long totalPacketsForOwner(@Param("userId") UUID userId);

    @Query("""
            SELECT AVG(a.riskScore) FROM Analysis a
            WHERE (:userId IS NULL OR a.uploadedFile.user.id = :userId)
              AND a.status = com.velorix.sentinel.entity.enums.AnalysisStatus.COMPLETED
            """)
    Double averageRiskScoreForOwner(@Param("userId") UUID userId);

    /** Completion timestamp and inspected volume, bucketed in memory into a daily series. */
    @Query("""
            SELECT a.completedAt, a.totalPackets FROM Analysis a
            WHERE (:userId IS NULL OR a.uploadedFile.user.id = :userId)
              AND a.completedAt IS NOT NULL
            ORDER BY a.completedAt
            """)
    List<Object[]> packetVolumeTimeline(@Param("userId") UUID userId, Pageable pageable);

}
