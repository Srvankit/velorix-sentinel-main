package com.velorix.sentinel.inspection.decoder;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import java.time.Instant;

/**
 * Fully decoded metadata for one frame, ready to be written as a {@code packets} row.
 *
 * <p>Every layer specific field is nullable because the layer may simply not exist: an
 * ARP frame has no TTL, an ICMP frame has no ports, a UDP frame has no window size.
 * Nulls are the honest representation - zero would be a fabricated value.</p>
 *
 * @param number          1-based ordinal inside the capture
 * @param timestamp       capture timestamp of the frame
 * @param sourceMac       layer 2 source address
 * @param destinationMac  layer 2 destination address
 * @param networkProtocol decoded layer 3 family
 * @param sourceIp        layer 3 source address, or the ARP sender address
 * @param destinationIp   layer 3 destination address, or the ARP target address
 * @param protocol        highest positively identified protocol
 * @param sourcePort      layer 4 source port
 * @param destinationPort layer 4 destination port
 * @param packetLength    frame length on the wire
 * @param capturedLength  bytes actually stored in the capture
 * @param payloadSize     bytes above the highest decoded header
 * @param ttl             IPv4 TTL or IPv6 hop limit
 * @param tcpFlags        comma separated TCP flag mnemonics
 * @param sequenceNumber  TCP sequence number, unsigned
 * @param acknowledgementNumber TCP acknowledgement number, unsigned
 * @param windowSize      TCP window size, unsigned
 * @param checksum        checksum of the highest decoded header, unsigned
 * @param info            short human readable description of the frame
 * @param malformed       true when the frame could not be fully decoded
 */
public record DecodedPacket(
        long number,
        Instant timestamp,
        String sourceMac,
        String destinationMac,
        NetworkProtocol networkProtocol,
        String sourceIp,
        String destinationIp,
        Protocol protocol,
        Integer sourcePort,
        Integer destinationPort,
        int packetLength,
        int capturedLength,
        int payloadSize,
        Integer ttl,
        String tcpFlags,
        Long sequenceNumber,
        Long acknowledgementNumber,
        Integer windowSize,
        Integer checksum,
        String info,
        boolean malformed) {
}
