package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * One inspection run over an {@link UploadedFile}.
 *
 * <p>Counters and the risk score are denormalised on purpose: dashboards read them
 * constantly and must never aggregate millions of packet rows on the fly.</p>
 */
@Entity
@Table(
        name = "analyses",
        indexes = {
                @Index(name = "idx_analyses_file", columnList = "uploaded_file_id"),
                @Index(name = "idx_analyses_status", columnList = "status"),
                @Index(name = "idx_analyses_created_at", columnList = "created_at")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Analysis extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "uploaded_file_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_analyses_uploaded_file"))
    private UploadedFile uploadedFile;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status = AnalysisStatus.QUEUED;

    /** Fine grained pipeline position, driving the progress stepper in the UI. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "stage", nullable = false, length = 20)
    private AnalysisStage stage = AnalysisStage.PREPARING;

    /** Frames decoded so far; equals {@link #totalPackets} once the run completes. */
    @Builder.Default
    @Column(name = "processed_packets", nullable = false)
    private long processedPackets = 0L;

    /** Completion percentage between 0 and 100, derived from bytes consumed. */
    @Builder.Default
    @Column(name = "progress_percent", nullable = false)
    private int progressPercent = 0;

    /** Frames recorded but not fully decodable. */
    @Builder.Default
    @Column(name = "malformed_packets", nullable = false)
    private long malformedPackets = 0L;

    /** True when the engine stopped at the configured packet ceiling. */
    @Builder.Default
    @Column(name = "is_truncated", nullable = false)
    private boolean truncated = false;

    /** Timestamp of the first frame in the capture. */
    @Column(name = "capture_started_at")
    private Instant captureStartedAt;

    /** Timestamp of the last frame in the capture. */
    @Column(name = "capture_ended_at")
    private Instant captureEndedAt;

    /** Wall clock span covered by the capture itself, in milliseconds. */
    @Column(name = "capture_duration_ms")
    private Long captureDuration;

    /** Mean wire length across every decoded frame. */
    @Builder.Default
    @Column(name = "average_packet_size", nullable = false)
    private double averagePacketSize = 0d;

    @Builder.Default
    @Column(name = "unique_source_ips", nullable = false)
    private long uniqueSourceIps = 0L;

    @Builder.Default
    @Column(name = "unique_destination_ips", nullable = false)
    private long uniqueDestinationIps = 0L;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Wall clock duration of the run, in milliseconds. */
    @Column(name = "duration_ms")
    private Long duration;

    @Builder.Default
    @Column(name = "total_packets", nullable = false)
    private long totalPackets = 0L;

    @Builder.Default
    @Column(name = "malicious_packets", nullable = false)
    private long maliciousPackets = 0L;

    @Builder.Default
    @Column(name = "safe_packets", nullable = false)
    private long safePackets = 0L;

    /** Aggregate risk between 0 and 100. */
    @Builder.Default
    @Column(name = "risk_score", nullable = false)
    private int riskScore = 0;

    /** Version of the detection engine that produced the results. */
    @Column(name = "analysis_version", nullable = false, length = 20)
    private String analysisVersion;

    /** Populated only when {@link AnalysisStatus#FAILED}. */
    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Packet> packets = new ArrayList<>();

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Threat> threats = new ArrayList<>();

    @ToString.Exclude
    @OneToOne(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AnalysisSummary summary;
}
