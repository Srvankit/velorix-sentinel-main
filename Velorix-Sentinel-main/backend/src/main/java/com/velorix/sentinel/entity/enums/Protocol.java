package com.velorix.sentinel.entity.enums;

/**
 * Transport / application protocol observed on a captured packet.
 *
 * <p>This is the <em>highest</em> layer the inspection engine could positively identify:
 * a frame carrying a DNS query over UDP is recorded as {@link #DNS}, not {@link #UDP}.
 * The network layer is tracked separately by {@link NetworkProtocol}.</p>
 */
public enum Protocol {

    TCP,
    UDP,
    ICMP,
    ICMPV6,
    ARP,
    DNS,
    DHCP,
    HTTP,
    HTTPS,
    TLS,
    OTHER
}
