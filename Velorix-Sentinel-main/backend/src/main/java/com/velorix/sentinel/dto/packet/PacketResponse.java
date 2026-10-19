package com.velorix.sentinel.dto.packet;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "PacketResponse", description = "A decoded frame belonging to an analysis")
public record PacketResponse(
        UUID id,
        UUID analysisId,
        long packetNumber,
        Instant timestamp,
        String sourceIp,
        String destinationIp,
        Integer sourcePort,
        Integer destinationPort,
        Protocol protocol,
        NetworkProtocol networkProtocol,
        int packetLength,
        String tcpFlags,
        int payloadSize,
        String info,
        boolean malformed,
        boolean suspicious) {
}
