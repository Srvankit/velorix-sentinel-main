package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A single decoded frame belonging to an {@link Analysis}.
 *
 * <p>High cardinality table: it is always read through pagination and never eagerly
 * loaded from the analysis aggregate.</p>
 */
@Entity
@Table(
        name = "packets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_packets_analysis_number",
                columnNames = {"analysis_id", "packet_number"}),
        indexes = {
                @Index(name = "idx_packets_analysis", columnList = "analysis_id"),
                @Index(name = "idx_packets_source_ip", columnList = "source_ip"),
                @Index(name = "idx_packets_destination_ip", columnList = "destination_ip"),
                @Index(name = "idx_packets_protocol", columnList = "protocol"),
                @Index(name = "idx_packets_suspicious", columnList = "is_suspicious")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Packet extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_packets_analysis"))
    private Analysis analysis;

    /** 1-based ordinal of the frame inside the capture. */
    @Column(name = "packet_number", nullable = false)
    private long packetNumber;

    /** Capture timestamp of the frame. */
    @Column(name = "packet_timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "source_ip", nullable = false, length = 45)
    private String sourceIp;

    @Column(name = "destination_ip", nullable = false, length = 45)
    private String destinationIp;

    /** Null for protocols without ports (ICMP, ARP). */
    @Column(name = "source_port")
    private Integer sourcePort;

    @Column(name = "destination_port")
    private Integer destinationPort;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "protocol", nullable = false, length = 10)
    private Protocol protocol = Protocol.OTHER;

    @Column(name = "packet_length", nullable = false)
    private int packetLength;

    /** Raw TCP flag mnemonics, e.g. {@code SYN,ACK}. */
    @Column(name = "tcp_flags", length = 40)
    private String tcpFlags;

    @Builder.Default
    @Column(name = "payload_size", nullable = false)
    private int payloadSize = 0;

    @Builder.Default
    @Column(name = "is_suspicious", nullable = false)
    private boolean suspicious = false;

    /** Layer 3 family; kept beside {@link #protocol} so address family can be filtered on. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "network_protocol", nullable = false, length = 10)
    private NetworkProtocol networkProtocol = NetworkProtocol.OTHER;

    /** Ethernet source address, null for captures without a link layer header. */
    @Column(name = "source_mac", length = 17)
    private String sourceMac;

    @Column(name = "destination_mac", length = 17)
    private String destinationMac;

    /** Bytes stored in the capture; lower than {@link #packetLength} on snaplen truncation. */
    @Builder.Default
    @Column(name = "captured_length", nullable = false)
    private int capturedLength = 0;

    /** IPv4 time to live or IPv6 hop limit. */
    @Column(name = "ttl")
    private Integer ttl;

    /** TCP sequence number, held as a long because the wire value is unsigned 32-bit. */
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "acknowledgement_number")
    private Long acknowledgementNumber;

    @Column(name = "window_size")
    private Integer windowSize;

    /** Checksum of the highest decoded header, unsigned. */
    @Column(name = "checksum")
    private Integer checksum;

    /** Short human readable description, e.g. {@code HTTP 51234 -> 80 [PSH,ACK] len=517}. */
    @Column(name = "info", length = 255)
    private String info;

    /** True when the frame could not be fully decoded; it is still recorded. */
    @Builder.Default
    @Column(name = "is_malformed", nullable = false)
    private boolean malformed = false;
}
