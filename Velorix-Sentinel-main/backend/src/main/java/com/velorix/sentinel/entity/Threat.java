package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatStatus;
import com.velorix.sentinel.entity.enums.ThreatType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A finding raised by the detection engine for a given {@link Analysis}.
 *
 * <p>A threat is an aggregate, not a packet: one row describes a behaviour observed over
 * many frames, and keeps the sample frame numbers that justify it so an analyst can pivot
 * straight back into the packet table.</p>
 */
@Entity
@Table(
        name = "threats",
        indexes = {
                @Index(name = "idx_threats_analysis", columnList = "analysis_id"),
                @Index(name = "idx_threats_type", columnList = "threat_type"),
                @Index(name = "idx_threats_severity", columnList = "severity"),
                @Index(name = "idx_threats_detected_at", columnList = "detected_at"),
                @Index(name = "idx_threats_rule", columnList = "detection_rule"),
                @Index(name = "idx_threats_source_ip", columnList = "source_ip"),
                @Index(name = "idx_threats_destination_ip", columnList = "destination_ip")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Threat extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_threats_analysis"))
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "threat_type", nullable = false, length = 60)
    private ThreatType threatType = ThreatType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "severity", nullable = false, length = 10)
    private ThreatSeverity severity = ThreatSeverity.LOW;

    /** Detector confidence between 0.00 and 1.00; exposed to clients as a percentage. */
    @Builder.Default
    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 0.0d;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "recommendation", length = 2000)
    private String recommendation;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /* ------------------------------------------------------------ provenance */

    /** Stable identifier of the rule that raised the finding, e.g. {@code port-scan}. */
    @Builder.Default
    @Column(name = "detection_rule", nullable = false, length = 60)
    private String detectionRule = "manual";

    @Builder.Default
    @Column(name = "rule_version", nullable = false, length = 20)
    private String ruleVersion = "1.0.0";

    /** Human readable justification: counters, windows and thresholds that fired. */
    @Builder.Default
    @Column(name = "evidence", nullable = false, length = 4000)
    private String evidence = "";

    /** MITRE ATT&CK technique identifier, null when the category has no mapping. */
    @Column(name = "mitre_technique", length = 20)
    private String mitreTechnique;

    @Column(name = "mitre_technique_name", length = 120)
    private String mitreTechniqueName;

    /* ------------------------------------------------------------ affected hosts */

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "protocol", nullable = false, length = 10)
    private Protocol protocol = Protocol.OTHER;

    /** Number of frames that contributed to the finding. */
    @Builder.Default
    @Column(name = "packet_count", nullable = false)
    private long packetCount = 0L;

    /** Comma separated sample of contributing packet numbers, capped by the engine. */
    @Column(name = "sample_packet_numbers", length = 1000)
    private String samplePacketNumbers;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private ThreatStatus status = ThreatStatus.OPEN;
}
