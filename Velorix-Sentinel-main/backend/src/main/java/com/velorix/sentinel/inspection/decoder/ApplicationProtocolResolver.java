package com.velorix.sentinel.inspection.decoder;

import com.velorix.sentinel.entity.enums.Protocol;

/**
 * Identifies the application protocol riding on a transport segment.
 *
 * <p>Two signals are combined, cheapest first: the well known port, then a lightweight
 * look at the first payload bytes. The payload check exists because ports lie - HTTP on
 * 8081 and TLS on 8443 are routine - and because it is the only way to recognise TLS
 * without a full handshake parser.</p>
 *
 * <p>Nothing here inspects payload <em>content</em>: only the few framing bytes needed to
 * name the protocol are read, so no user data is interpreted or stored.</p>
 */
final class ApplicationProtocolResolver {

    private static final int PORT_DNS = 53;
    private static final int PORT_MDNS = 5353;
    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTP_ALT = 8080;
    private static final int PORT_HTTP_ALT2 = 8000;
    private static final int PORT_HTTPS = 443;
    private static final int PORT_HTTPS_ALT = 8443;
    private static final int PORT_DHCP_SERVER = 67;
    private static final int PORT_DHCP_CLIENT = 68;
    private static final int PORT_DHCPV6_CLIENT = 546;
    private static final int PORT_DHCPV6_SERVER = 547;

    /** TLS record content type {@code handshake} followed by a 3.x version major. */
    private static final byte TLS_RECORD_HANDSHAKE = 0x16;
    private static final byte TLS_VERSION_MAJOR = 0x03;

    private ApplicationProtocolResolver() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * @param transport the decoded transport protocol, used as the fallback answer
     * @param payload   payload bytes above the transport header; may be empty
     * @return the most specific protocol that could be identified
     */
    static Protocol resolve(Protocol transport, int sourcePort, int destinationPort, byte[] payload) {
        if (transport == Protocol.UDP) {
            if (matches(sourcePort, destinationPort, PORT_DNS) || matches(sourcePort, destinationPort, PORT_MDNS)) {
                return Protocol.DNS;
            }
            if (matches(sourcePort, destinationPort, PORT_DHCP_SERVER)
                    || matches(sourcePort, destinationPort, PORT_DHCP_CLIENT)
                    || matches(sourcePort, destinationPort, PORT_DHCPV6_CLIENT)
                    || matches(sourcePort, destinationPort, PORT_DHCPV6_SERVER)) {
                return Protocol.DHCP;
            }
            return transport;
        }
        if (transport != Protocol.TCP) {
            return transport;
        }

        if (matches(sourcePort, destinationPort, PORT_DNS)) {
            return Protocol.DNS;
        }
        if (matches(sourcePort, destinationPort, PORT_HTTPS) || matches(sourcePort, destinationPort, PORT_HTTPS_ALT)) {
            return Protocol.HTTPS;
        }
        if (looksLikeTls(payload)) {
            return Protocol.TLS;
        }
        if (matches(sourcePort, destinationPort, PORT_HTTP)
                || matches(sourcePort, destinationPort, PORT_HTTP_ALT)
                || matches(sourcePort, destinationPort, PORT_HTTP_ALT2)
                || looksLikeHttp(payload)) {
            return Protocol.HTTP;
        }
        return Protocol.TCP;
    }

    private static boolean matches(int sourcePort, int destinationPort, int wellKnown) {
        return sourcePort == wellKnown || destinationPort == wellKnown;
    }

    /** A TLS record starts with the content type and a {@code 3.x} protocol version. */
    private static boolean looksLikeTls(byte[] payload) {
        return payload.length >= 3
                && payload[0] == TLS_RECORD_HANDSHAKE
                && payload[1] == TLS_VERSION_MAJOR;
    }

    /** Matches an HTTP request line verb or a {@code HTTP/} status line prefix. */
    private static boolean looksLikeHttp(byte[] payload) {
        if (payload.length < 5) {
            return false;
        }
        String prefix = new String(payload, 0, Math.min(payload.length, 8), java.nio.charset.StandardCharsets.US_ASCII);
        return prefix.startsWith("GET ")
                || prefix.startsWith("POST ")
                || prefix.startsWith("PUT ")
                || prefix.startsWith("HEAD ")
                || prefix.startsWith("DELETE ")
                || prefix.startsWith("PATCH ")
                || prefix.startsWith("OPTIONS ")
                || prefix.startsWith("HTTP/");
    }
}
