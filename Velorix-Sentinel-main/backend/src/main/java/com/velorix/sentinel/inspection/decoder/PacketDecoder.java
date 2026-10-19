package com.velorix.sentinel.inspection.decoder;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.util.ArrayList;
import java.util.List;
import org.pcap4j.packet.ArpPacket;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IcmpV4CommonPacket;
import org.pcap4j.packet.IcmpV6CommonPacket;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import org.springframework.stereotype.Component;

/**
 * Turns a raw {@link CapturedFrame} into structured {@link DecodedPacket} metadata using
 * the pure Java Pcap4J decoders.
 *
 * <p>Only the decoding half of Pcap4J is used - no native {@code libpcap} is ever loaded,
 * because captures are read from files rather than from a live interface.</p>
 *
 * <p>The decoder is deliberately total: a frame that Pcap4J refuses to parse, a truncated
 * capture or an unknown ethertype produces a {@code malformed} row rather than an
 * exception. Real captures are full of damaged frames, and losing the whole run over one
 * bad packet would be unacceptable.</p>
 */
@Component
public class PacketDecoder {

    /** libpcap link layer types this decoder understands. */
    private static final int LINKTYPE_ETHERNET = 1;
    private static final int LINKTYPE_RAW = 101;
    private static final int LINKTYPE_IPV4 = 228;
    private static final int LINKTYPE_IPV6 = 229;
    private static final int LINKTYPE_LINUX_SLL = 113;
    private static final int LINUX_SLL_HEADER_BYTES = 16;

    private static final int IPV4_VERSION_NIBBLE = 4;
    private static final int IPV6_VERSION_NIBBLE = 6;
    private static final String UNKNOWN_ADDRESS = "-";
    private static final int MAX_INFO_LENGTH = 255;

    /**
     * Decodes one frame. Never throws for frame level problems.
     *
     * @param frame the frame to decode; its buffer is only read inside this call
     */
    public DecodedPacket decode(CapturedFrame frame) {
        Builder builder = new Builder(frame);
        try {
            Packet root = parseLinkLayer(frame);
            if (root == null) {
                return builder.unsupported("Unsupported link layer type " + frame.linkType());
            }
            if (root instanceof EthernetPacket ethernet) {
                builder.ethernet(ethernet);
                decodeNetworkLayer(ethernet.getPayload(), builder);
            } else {
                decodeNetworkLayer(root, builder);
            }
            return builder.build();
        } catch (IllegalRawDataException ex) {
            return builder.unsupported("Malformed frame: " + ex.getMessage());
        } catch (RuntimeException ex) {
            // Pcap4J throws unchecked errors on some corrupted option fields.
            return builder.unsupported("Undecodable frame: " + ex.getClass().getSimpleName());
        }
    }

    /** Builds the outermost packet for the capture's link layer, or null if unsupported. */
    private Packet parseLinkLayer(CapturedFrame frame) throws IllegalRawDataException {
        byte[] data = frame.data();
        int offset = frame.offset();
        int length = frame.capturedLength();

        return switch (frame.linkType()) {
            case LINKTYPE_ETHERNET -> EthernetPacket.newPacket(data, offset, length);
            case LINKTYPE_IPV4 -> IpV4Packet.newPacket(data, offset, length);
            case LINKTYPE_IPV6 -> IpV6Packet.newPacket(data, offset, length);
            case LINKTYPE_RAW -> parseRawIp(data, offset, length);
            case LINKTYPE_LINUX_SLL -> length > LINUX_SLL_HEADER_BYTES
                    ? parseRawIp(data, offset + LINUX_SLL_HEADER_BYTES, length - LINUX_SLL_HEADER_BYTES)
                    : null;
            default -> null;
        };
    }

    /** Raw IP captures carry no link header: the IP version nibble selects the decoder. */
    private Packet parseRawIp(byte[] data, int offset, int length) throws IllegalRawDataException {
        if (length < 1) {
            return null;
        }
        int version = (data[offset] & 0xF0) >> 4;
        return switch (version) {
            case IPV4_VERSION_NIBBLE -> IpV4Packet.newPacket(data, offset, length);
            case IPV6_VERSION_NIBBLE -> IpV6Packet.newPacket(data, offset, length);
            default -> null;
        };
    }

    private void decodeNetworkLayer(Packet packet, Builder builder) {
        if (packet instanceof IpV4Packet ipv4) {
            IpV4Packet.IpV4Header header = ipv4.getHeader();
            builder.network(
                    NetworkProtocol.IPV4,
                    header.getSrcAddr().getHostAddress(),
                    header.getDstAddr().getHostAddress());
            builder.ttl(Byte.toUnsignedInt(header.getTtl()));
            builder.checksum(Short.toUnsignedInt(header.getHeaderChecksum()));
            decodeTransportLayer(ipv4.getPayload(), builder);
        } else if (packet instanceof IpV6Packet ipv6) {
            IpV6Packet.IpV6Header header = ipv6.getHeader();
            builder.network(
                    NetworkProtocol.IPV6,
                    header.getSrcAddr().getHostAddress(),
                    header.getDstAddr().getHostAddress());
            builder.ttl(Byte.toUnsignedInt(header.getHopLimit()));
            decodeTransportLayer(ipv6.getPayload(), builder);
        } else if (packet instanceof ArpPacket arp) {
            ArpPacket.ArpHeader header = arp.getHeader();
            builder.network(
                    NetworkProtocol.ARP,
                    header.getSrcProtocolAddr().getHostAddress(),
                    header.getDstProtocolAddr().getHostAddress());
            builder.protocol(Protocol.ARP);
            builder.info("ARP %s %s -> %s".formatted(
                    header.getOperation().name(),
                    header.getSrcProtocolAddr().getHostAddress(),
                    header.getDstProtocolAddr().getHostAddress()));
        } else {
            builder.network(NetworkProtocol.OTHER, UNKNOWN_ADDRESS, UNKNOWN_ADDRESS);
            builder.protocol(Protocol.OTHER);
        }
    }

    private void decodeTransportLayer(Packet packet, Builder builder) {
        if (packet instanceof TcpPacket tcp) {
            decodeTcp(tcp, builder);
        } else if (packet instanceof UdpPacket udp) {
            decodeUdp(udp, builder);
        } else if (packet instanceof IcmpV4CommonPacket icmp) {
            builder.protocol(Protocol.ICMP);
            builder.checksum(Short.toUnsignedInt(icmp.getHeader().getChecksum()));
            builder.payload(payloadLength(icmp));
            builder.info("ICMP " + icmp.getHeader().getType().name());
        } else if (packet instanceof IcmpV6CommonPacket icmpv6) {
            builder.protocol(Protocol.ICMPV6);
            builder.checksum(Short.toUnsignedInt(icmpv6.getHeader().getChecksum()));
            builder.payload(payloadLength(icmpv6));
            builder.info("ICMPv6 " + icmpv6.getHeader().getType().name());
        } else {
            builder.protocol(Protocol.OTHER);
            builder.payload(packet == null ? 0 : packet.length());
        }
    }

    private void decodeTcp(TcpPacket tcp, Builder builder) {
        TcpPacket.TcpHeader header = tcp.getHeader();
        int sourcePort = header.getSrcPort().valueAsInt();
        int destinationPort = header.getDstPort().valueAsInt();
        byte[] payload = tcp.getPayload() == null ? new byte[0] : tcp.getPayload().getRawData();

        builder.ports(sourcePort, destinationPort);
        builder.payload(payload.length);
        builder.tcp(
                flagsOf(header),
                Integer.toUnsignedLong(header.getSequenceNumber()),
                Integer.toUnsignedLong(header.getAcknowledgmentNumber()),
                Short.toUnsignedInt(header.getWindow()));
        builder.checksum(Short.toUnsignedInt(header.getChecksum()));

        Protocol protocol = ApplicationProtocolResolver.resolve(
                Protocol.TCP, sourcePort, destinationPort, payload);
        builder.protocol(protocol);
        builder.info("%s %d -> %d [%s] len=%d".formatted(
                protocol.name(), sourcePort, destinationPort, builder.tcpFlags, payload.length));
    }

    private void decodeUdp(UdpPacket udp, Builder builder) {
        UdpPacket.UdpHeader header = udp.getHeader();
        int sourcePort = header.getSrcPort().valueAsInt();
        int destinationPort = header.getDstPort().valueAsInt();
        byte[] payload = udp.getPayload() == null ? new byte[0] : udp.getPayload().getRawData();

        builder.ports(sourcePort, destinationPort);
        builder.payload(payload.length);
        builder.checksum(Short.toUnsignedInt(header.getChecksum()));

        Protocol protocol = ApplicationProtocolResolver.resolve(
                Protocol.UDP, sourcePort, destinationPort, payload);
        builder.protocol(protocol);
        builder.info("%s %d -> %d len=%d".formatted(protocol.name(), sourcePort, destinationPort, payload.length));
    }

    private int payloadLength(Packet packet) {
        return packet.getPayload() == null ? 0 : packet.getPayload().length();
    }

    /** Renders the TCP control bits in the conventional Wireshark order. */
    private String flagsOf(TcpPacket.TcpHeader header) {
        List<String> flags = new ArrayList<>(8);
        if (header.getFin()) {
            flags.add("FIN");
        }
        if (header.getSyn()) {
            flags.add("SYN");
        }
        if (header.getRst()) {
            flags.add("RST");
        }
        if (header.getPsh()) {
            flags.add("PSH");
        }
        if (header.getAck()) {
            flags.add("ACK");
        }
        if (header.getUrg()) {
            flags.add("URG");
        }
        // ECE and CWR are not exposed as named accessors by Pcap4J's TCP header, so the
        // flag string stops at the six control bits it does expose.

        return String.join(",", flags);
    }

    /** Mutable accumulator: layers fill in what they know as the frame is walked down. */
    private static final class Builder {

        private final CapturedFrame frame;
        private String sourceMac;
        private String destinationMac;
        private NetworkProtocol networkProtocol = NetworkProtocol.OTHER;
        private String sourceIp = UNKNOWN_ADDRESS;
        private String destinationIp = UNKNOWN_ADDRESS;
        private Protocol protocol = Protocol.OTHER;
        private Integer sourcePort;
        private Integer destinationPort;
        private int payloadSize;
        private Integer ttl;
        private String tcpFlags;
        private Long sequenceNumber;
        private Long acknowledgementNumber;
        private Integer windowSize;
        private Integer checksum;
        private String info;

        private Builder(CapturedFrame frame) {
            this.frame = frame;
        }

        void ethernet(EthernetPacket ethernet) {
            EthernetPacket.EthernetHeader header = ethernet.getHeader();
            this.sourceMac = header.getSrcAddr().toString();
            this.destinationMac = header.getDstAddr().toString();
        }

        void network(NetworkProtocol value, String source, String destination) {
            this.networkProtocol = value;
            this.sourceIp = source;
            this.destinationIp = destination;
        }

        void protocol(Protocol value) {
            this.protocol = value;
        }

        void ports(int source, int destination) {
            this.sourcePort = source;
            this.destinationPort = destination;
        }

        void payload(int size) {
            this.payloadSize = Math.max(size, 0);
        }

        void ttl(int value) {
            this.ttl = value;
        }

        void checksum(int value) {
            this.checksum = value;
        }

        void tcp(String flags, long sequence, long acknowledgement, int window) {
            this.tcpFlags = flags;
            this.sequenceNumber = sequence;
            this.acknowledgementNumber = acknowledgement;
            this.windowSize = window;
        }

        void info(String value) {
            this.info = value;
        }

        DecodedPacket unsupported(String reason) {
            this.info = reason;
            this.protocol = Protocol.OTHER;
            return build(true);
        }

        DecodedPacket build() {
            return build(false);
        }

        private DecodedPacket build(boolean malformed) {
            return new DecodedPacket(
                    frame.number(),
                    frame.timestamp(),
                    sourceMac,
                    destinationMac,
                    networkProtocol,
                    sourceIp,
                    destinationIp,
                    protocol,
                    sourcePort,
                    destinationPort,
                    frame.originalLength(),
                    frame.capturedLength(),
                    payloadSize,
                    ttl,
                    tcpFlags,
                    sequenceNumber,
                    acknowledgementNumber,
                    windowSize,
                    checksum,
                    truncate(info),
                    malformed);
        }

        private static String truncate(String value) {
            if (value == null) {
                return null;
            }
            return value.length() <= MAX_INFO_LENGTH ? value : value.substring(0, MAX_INFO_LENGTH);
        }
    }
}
