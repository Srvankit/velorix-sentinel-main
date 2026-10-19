package com.velorix.sentinel.entity.enums;

/**
 * Network (layer 3) protocol carrying a decoded frame.
 *
 * <p>Kept separate from {@link Protocol} so a packet can be filtered by address family
 * ("show me every IPv6 conversation") independently of its application protocol.</p>
 */
public enum NetworkProtocol {

    IPV4,
    IPV6,
    ARP,
    /** No layer 3 could be decoded: unknown ethertype, malformed or truncated frame. */
    OTHER
}
