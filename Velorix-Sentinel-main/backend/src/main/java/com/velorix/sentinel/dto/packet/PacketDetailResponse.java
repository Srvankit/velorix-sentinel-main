package com.velorix.sentinel.dto.packet;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Every field the inspection engine extracted for one frame, served by
 * {@code GET /api/v1/analysis/{id}/packets/{packetId}} and rendered in the detail drawer.
 *
 * <p>Layer specific fields are null when the layer is absent - an ARP frame has no TTL,
 * a UDP datagram has no window size. Null means "not applicable", never "zero".</p>
 */
@Schema(name = "PacketDetailResponse", description = "Full decoded metadata for a single frame")
public record PacketDetailResponse(
        UUID id,
        UUID analysisId,
        long packetNumber,
        Instant timestamp,

        @Schema(description = "Ethernet source address") String sourceMac,
        @Schema(description = "Ethernet destination address") String destinationMac,

        NetworkProtocol networkProtocol,
        String sourceIp,
        String destinationIp,
        @Schema(description = "IPv4 time to live or IPv6 hop limit") Integer ttl,

        Protocol protocol,
        Integer sourcePort,
        Integer destinationPort,
        @Schema(description = "TCP control bits, e.g. SYN,ACK") String tcpFlags,
        @Schema(description = "Unsigned TCP sequence number") Long sequenceNumber,
        @Schema(description = "Unsigned TCP acknowledgement number") Long acknowledgementNumber,
        Integer windowSize,
        @Schema(description = "Checksum of the highest decoded header") Integer checksum,

        @Schema(description = "Frame length on the wire") int packetLength,
        @Schema(description = "Bytes actually stored in the capture") int capturedLength,
        int payloadSize,

        String info,
        @Schema(description = "True when the frame could not be fully decoded") boolean malformed,
        boolean suspicious) {
}
