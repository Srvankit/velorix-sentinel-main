package com.velorix.sentinel.detection;

/**
 * Verified packet-level metrics derived from inspection metadata and detection evidence.
 *
 * @param totalPackets        total frames decoded and stored for the analysis
 * @param maliciousPackets    frames associated with genuine detection findings
 * @param safePackets         frames positively verified as non-malicious and fully decoded
 * @param unclassifiedPackets frames that were malformed or of unrecognised protocol and thus cannot be deemed safe
 */
public record PacketCounters(
        long totalPackets,
        long maliciousPackets,
        long safePackets,
        long unclassifiedPackets) {
}
