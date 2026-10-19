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

    void deleteByAnalysisId(UUID analysisId);
}
