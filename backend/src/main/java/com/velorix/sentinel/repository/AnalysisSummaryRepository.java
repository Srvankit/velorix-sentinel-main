package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.AnalysisSummary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisSummaryRepository extends JpaRepository<AnalysisSummary, UUID> {

    Optional<AnalysisSummary> findByAnalysisId(UUID analysisId);

    boolean existsByAnalysisId(UUID analysisId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(s) > 0 FROM AnalysisSummary s WHERE (:userId IS NULL OR s.analysis.uploadedFile.user.id = :userId)")
    boolean existsForOwner(@org.springframework.data.repository.query.Param("userId") UUID userId);

    void deleteByAnalysisId(UUID analysisId);
}
