package com.velorix.sentinel.inspection.decoder;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.util.ArrayList;
import java.util.List;
import org.pcap4j.packet.ArpPacket;
import org.pcap4j.packet.Dot1qVlanTagPacket;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IcmpV4CommonPacket;
import org.pcap4j.packet.IcmpV6CommonPacket;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6ExtDestinationOptionsPacket;
import org.pcap4j.packet.IpV6ExtFragmentPacket;
import org.pcap4j.packet.IpV6ExtHopByHopOptionsPacket;
import org.pcap4j.packet.IpV6ExtRoutingPacket;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
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
    private static final int MAX_VLAN_DEPTH = 3;
    private static final int MAX_IPV6_EXT_DEPTH = 4;

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
                decodeEthernetPayload(ethernet.getPayload(), builder);
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

    /** Unwraps 802.1Q (and stacked QinQ) VLAN headers before passing to the network layer. */
    private void decodeEthernetPayload(Packet payload, Builder builder) {
        Packet current = payload;
        int depth = 0;
        Integer outerVlanId = null;
        Integer innerVlanId = null;

        while (current instanceof Dot1qVlanTagPacket vlan) {
            depth++;
            int vid = vlan.getHeader().getVidAsInt();
            if (outerVlanId == null) {
                outerVlanId = vid;
            } else if (innerVlanId == null) {
                innerVlanId = vid;
            }
            if (depth >= MAX_VLAN_DEPTH) {
                // Stacked beyond supported limit (e.g. triple tagging) - halt recursion conservatively
                break;
            }
            current = vlan.getPayload();
        }

        if (outerVlanId != null) {
            builder.vlan(outerVlanId, innerVlanId);
        }

        decodeNetworkLayer(current, builder);
    }

    private void decodeNetworkLayer(Packet packet, Builder builder) {
        if (packet instanceof IpV4Packet ipv4) {
            decodeIpV4(ipv4, builder);
        } else if (packet instanceof IpV6Packet ipv6) {
            decodeIpV6(ipv6, builder);
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
            builder.payload(packet == null ? 0 : packet.length());
        }
    }

    private void decodeIpV4(IpV4Packet ipv4, Builder builder) {
        IpV4Packet.IpV4Header header = ipv4.getHeader();
        builder.network(
                NetworkProtocol.IPV4,
                header.getSrcAddr().getHostAddress(),
                header.getDstAddr().getHostAddress());
        builder.ttl(Byte.toUnsignedInt(header.getTtl()));
        builder.checksum(Short.toUnsignedInt(header.getHeaderChecksum()));

        int fragmentOffset = Short.toUnsignedInt(header.getFragmentOffset());
        int fragmentOffsetBytes = fragmentOffset * 8;
        boolean moreFragments = header.getMoreFragmentFlag();
        int identification = Short.toUnsignedInt(header.getIdentification());

        if (fragmentOffset > 0) {
            // Non-initial fragment: payload does not contain transport headers.
            // Do not attempt to parse transport ports or TCP flags from arbitrary fragment payload.
            builder.protocol(Protocol.OTHER);
            int payloadLen = ipv4.getPayload() == null ? 0 : ipv4.getPayload().length();
            builder.payload(payloadLen);
            builder.info("IPv4 Fragment [offset=%d, more=%b, id=0x%04X, len=%d]".formatted(
                    fragmentOffsetBytes, moreFragments, identification, payloadLen));
        } else {
            // Offset 0: either unfragmented or initial fragment
            Packet payload = ipv4.getPayload();
            if (payload != null && !(payload instanceof TcpPacket || payload instanceof UdpPacket || payload instanceof IcmpV4CommonPacket)) {
                // When moreFragments is true, Pcap4J skips upper-layer packet factories (returning FragmentedPacket/UnknownPacket).
                // For initial fragments (offset == 0), parse the transport layer if available.
                Packet parsed = parseTransportPayload(header.getProtocol(), payload.getRawData());
                if (parsed != null) {
                    payload = parsed;
                }
            }
            decodeTransportLayer(payload, builder);
            if (moreFragments) {
                builder.annotateFragment(fragmentOffsetBytes, true);
            }
        }
    }

    private void decodeIpV6(IpV6Packet ipv6, Builder builder) {
        IpV6Packet.IpV6Header header = ipv6.getHeader();
        builder.network(
                NetworkProtocol.IPV6,
                header.getSrcAddr().getHostAddress(),
                header.getDstAddr().getHostAddress());
        builder.ttl(Byte.toUnsignedInt(header.getHopLimit()));

        Packet current = ipv6.getPayload();
        int depth = 0;
        IpV6ExtFragmentPacket fragmentHeader = null;

        while (current != null && depth < MAX_IPV6_EXT_DEPTH) {
            depth++;
            if (current instanceof IpV6ExtFragmentPacket frag) {
                fragmentHeader = frag;
                current = frag.getPayload();
                break;
            } else if (current instanceof IpV6ExtHopByHopOptionsPacket hop) {
                current = hop.getPayload();
            } else if (current instanceof IpV6ExtRoutingPacket routing) {
                current = routing.getPayload();
            } else if (current instanceof IpV6ExtDestinationOptionsPacket dest) {
                current = dest.getPayload();
            } else {
                break;
            }
        }

        if (fragmentHeader != null) {
            IpV6ExtFragmentPacket.IpV6ExtFragmentHeader fHeader = fragmentHeader.getHeader();
            int fragmentOffset = Short.toUnsignedInt(fHeader.getFragmentOffset());
            int fragmentOffsetBytes = fragmentOffset * 8;
            boolean moreFragments = fHeader.getM();
            int id = fHeader.getIdentification();

            if (fragmentOffset > 0) {
                // Non-initial IPv6 fragment: does not carry transport headers.
                builder.protocol(Protocol.OTHER);
                int payloadLen = current == null ? 0 : current.length();
                builder.payload(payloadLen);
                builder.info("IPv6 Fragment [offset=%d, more=%b, id=0x%08X, len=%d]".formatted(
                        fragmentOffsetBytes, moreFragments, id, payloadLen));
                return;
            } else {
                // Initial IPv6 fragment (offset 0)
                if (current != null && !(current instanceof TcpPacket || current instanceof UdpPacket || current instanceof IcmpV6CommonPacket)) {
                    Packet parsed = parseTransportPayload(fHeader.getNextHeader(), current.getRawData());
                    if (parsed != null) {
                        current = parsed;
                    }
                }
                decodeTransportLayer(current, builder);
                if (moreFragments) {
                    builder.annotateFragment(fragmentOffsetBytes, true);
                }
                return;
            }
        }

        // Standard unfragmented IPv6
        decodeTransportLayer(current, builder);
    }

    /**
     * Attempts to parse transport headers from raw payload bytes when upper layer parsing
     * was skipped by Pcap4J (such as for initial fragments).
     */
    private Packet parseTransportPayload(IpNumber ipNumber, byte[] rawData) {
        if (rawData == null || rawData.length == 0 || ipNumber == null) {
            return null;
        }
        try {
            if (IpNumber.TCP.equals(ipNumber)) {
                return TcpPacket.newPacket(rawData, 0, rawData.length);
            } else if (IpNumber.UDP.equals(ipNumber)) {
                return UdpPacket.newPacket(rawData, 0, rawData.length);
            } else if (IpNumber.ICMPV4.equals(ipNumber)) {
                return IcmpV4CommonPacket.newPacket(rawData, 0, rawData.length);
            } else if (IpNumber.ICMPV6.equals(ipNumber)) {
                return IcmpV6CommonPacket.newPacket(rawData, 0, rawData.length);
            }
        } catch (IllegalRawDataException | RuntimeException ignored) {
            // Cannot parse transport header (e.g. truncated fragment or malformed header)
        }
        return null;
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
        byte[] raw = header.getRawData();
        boolean cwr = raw != null && raw.length >= 14 && (raw[13] & 0x80) != 0;
        boolean ece = raw != null && raw.length >= 14 && (raw[13] & 0x40) != 0;

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
        if (ece) {
            flags.add("ECE");
        }
        if (cwr) {
            flags.add("CWR");
        }

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
        private Integer vlanId;
        private Integer innerVlanId;

        private Builder(CapturedFrame frame) {
            this.frame = frame;
        }

        void ethernet(EthernetPacket ethernet) {
            EthernetPacket.EthernetHeader header = ethernet.getHeader();
            this.sourceMac = header.getSrcAddr().toString();
            this.destinationMac = header.getDstAddr().toString();
        }

        void vlan(int outer, Integer inner) {
            this.vlanId = outer;
            this.innerVlanId = inner;
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

        void annotateFragment(int offsetBytes, boolean more) {
            String suffix = " (Fragment [offset=%d, more=%b])".formatted(offsetBytes, more);
            this.info = (this.info == null ? "" : this.info) + suffix;
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
            String formattedInfo = info;
            if (vlanId != null) {
                String prefix = innerVlanId != null
                        ? "[VLAN %d/%d] ".formatted(vlanId, innerVlanId)
                        : "[VLAN %d] ".formatted(vlanId);
                formattedInfo = prefix + (formattedInfo != null ? formattedInfo : "");
            }

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
                    truncate(formattedInfo),
                    malformed,
                    false,
                    vlanId);
        }

        private static String truncate(String value) {
            if (value == null) {
                return null;
            }
            return value.length() <= MAX_INFO_LENGTH ? value : value.substring(0, MAX_INFO_LENGTH);
        }
    }
}
