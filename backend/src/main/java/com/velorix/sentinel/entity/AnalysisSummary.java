package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.OverallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Pre-aggregated verdict for one {@link Analysis}, kept so dashboards never
 * recompute severity counters at read time.
 */
@Entity
@Table(
        name = "analysis_summaries",
        indexes = {
                @Index(name = "idx_analysis_summaries_analysis", columnList = "analysis_id", unique = true),
                @Index(name = "idx_analysis_summaries_status", columnList = "overall_status")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisSummary extends BaseEntity {

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_analysis_summaries_analysis"))
    private Analysis analysis;

    @Builder.Default
    @Column(name = "total_threats", nullable = false)
    private long totalThreats = 0L;

    @Builder.Default
    @Column(name = "critical_threats", nullable = false)
    private long criticalThreats = 0L;

    @Builder.Default
    @Column(name = "high_threats", nullable = false)
    private long highThreats = 0L;

    @Builder.Default
    @Column(name = "medium_threats", nullable = false)
    private long mediumThreats = 0L;

    @Builder.Default
    @Column(name = "low_threats", nullable = false)
    private long lowThreats = 0L;

    @Builder.Default
    @Column(name = "risk_score", nullable = false)
    private int riskScore = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "overall_status", nullable = false, length = 20)
    private OverallStatus overallStatus = OverallStatus.SECURE;
}
