package com.velorix.sentinel.entity.enums;

/**
 * Detection categories produced by the threat detection engine.
 *
 * <p>Each detection rule declares exactly one category. New rules add a value here and
 * nothing else in the engine changes.</p>
 */
public enum ThreatType {

    PORT_SCAN,
    SYN_SCAN,
    UDP_SCAN,
    DDOS,
    ICMP_FLOOD,
    DNS_ANOMALY,
    BRUTE_FORCE,
    REPEATED_CONNECTION,
    DATA_EXFILTRATION,
    LARGE_PAYLOAD,
    BEACONING,
    SUSPICIOUS_EXTERNAL_COMM,
    MALWARE,
    C2_COMMUNICATION,
    SUSPICIOUS_TRAFFIC,
    UNKNOWN_PROTOCOL,
    ABNORMAL_PACKET_SIZE,
    UNKNOWN
}
