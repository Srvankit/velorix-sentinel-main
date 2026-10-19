package com.velorix.sentinel.detection;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import java.time.Instant;

/**
 * Immutable, flat view of one decoded frame as consumed by detection rules.
 *
 * <p>Rules never touch JPA entities. A capture can hold hundreds of thousands of frames,
 * so the engine streams plain records that carry no persistence context and can be
 * discarded as soon as every rule has seen them.</p>
 */
public record PacketRecord(
        long number,
        Instant timestamp,
        String sourceIp,
        String destinationIp,
        Integer sourcePort,
        Integer destinationPort,
        Protocol protocol,
        NetworkProtocol networkProtocol,
        int packetLength,
        int payloadSize,
        String tcpFlags,
        boolean malformed) {

    private static final String SYN = "SYN";
    private static final String ACK = "ACK";
    private static final String RST = "RST";
    private static final String FIN = "FIN";

    public boolean hasFlag(String flag) {
        return tcpFlags != null && !tcpFlags.isEmpty() && containsToken(tcpFlags, flag);
    }

    /** A connection attempt: SYN set, ACK clear. */
    public boolean isSynOnly() {
        return protocol == Protocol.TCP && hasFlag(SYN) && !hasFlag(ACK);
    }

    /** A refused or torn down connection, i.e. evidence a probe hit a closed door. */
    public boolean isResetOrFin() {
        return hasFlag(RST) || hasFlag(FIN);
    }

    /** True when the frame carries transport ports at all. */
    public boolean hasPorts() {
        return sourcePort != null && destinationPort != null;
    }

    private static boolean containsToken(String flags, String token) {
        int from = 0;
        while (true) {
            int at = flags.indexOf(token, from);
            if (at < 0) {
                return false;
            }
            boolean leftClean = at == 0 || flags.charAt(at - 1) == ',';
            int end = at + token.length();
            boolean rightClean = end == flags.length() || flags.charAt(end) == ',';
            if (leftClean && rightClean) {
                return true;
            }
            from = end;
        }
    }
}
