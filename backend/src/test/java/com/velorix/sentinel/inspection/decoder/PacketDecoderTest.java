package com.velorix.sentinel.inspection.decoder;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.nio.ByteBuffer;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates Task 1.5: Protocol Decoder Expansion — 802.1Q VLAN, TCP Flags, and IPv4/IPv6 Fragmentation.
 */
class PacketDecoderTest {

    private final PacketDecoder decoder = new PacketDecoder();

    @Test
    @DisplayName("Untagged standard Ethernet IPv4 TCP packet decodes all layers accurately")
    void decodesStandardUntaggedEthernetIpv4Tcp() {
        byte[] frameBytes = buildEthernetIpv4Tcp(null, (short) 0, (byte) 0x02, 54321, 80);
        CapturedFrame frame = new CapturedFrame(
                1L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV4);
        assertThat(packet.sourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.destinationIp()).isEqualTo("192.168.1.20");
        assertThat(packet.protocol()).isEqualTo(Protocol.HTTP); // port 80 -> HTTP
        assertThat(packet.sourcePort()).isEqualTo(54321);
        assertThat(packet.destinationPort()).isEqualTo(80);
        assertThat(packet.tcpFlags()).isEqualTo("SYN");
        assertThat(packet.vlanId()).isNull();
        assertThat(packet.sourceMac()).isEqualTo("66:77:88:99:aa:bb");
        assertThat(packet.destinationMac()).isEqualTo("00:11:22:33:44:55");
    }

    @Test
    @DisplayName("802.1Q VLAN-tagged Ethernet IPv4 TCP packet unwraps VLAN and decodes IP/TCP")
    void decodesVlanTaggedIpv4Tcp() {
        // Tagged with VLAN ID 100
        byte[] frameBytes = buildEthernetIpv4Tcp(100, (short) 0, (byte) 0x02, 40001, 443);
        CapturedFrame frame = new CapturedFrame(
                2L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.vlanId()).isEqualTo(100);
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV4);
        assertThat(packet.sourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.destinationIp()).isEqualTo("192.168.1.20");
        assertThat(packet.protocol()).isEqualTo(Protocol.HTTPS); // port 443 -> HTTPS
        assertThat(packet.sourcePort()).isEqualTo(40001);
        assertThat(packet.destinationPort()).isEqualTo(443);
        assertThat(packet.tcpFlags()).isEqualTo("SYN");
        assertThat(packet.info()).contains("[VLAN 100]");
    }

    @Test
    @DisplayName("802.1Q VLAN-tagged Ethernet IPv6 TCP packet unwraps VLAN and decodes IPv6/TCP")
    void decodesVlanTaggedIpv6Tcp() {
        byte[] frameBytes = buildEthernetIpv6Tcp(200, 50001, 80);
        CapturedFrame frame = new CapturedFrame(
                3L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.vlanId()).isEqualTo(200);
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV6);
        assertThat(packet.sourceIp()).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(packet.destinationIp()).isEqualTo("2001:db8:0:0:0:0:0:2");
        assertThat(packet.protocol()).isEqualTo(Protocol.HTTP);
        assertThat(packet.sourcePort()).isEqualTo(50001);
        assertThat(packet.destinationPort()).isEqualTo(80);
        assertThat(packet.info()).contains("[VLAN 200]");
    }

    @Test
    @DisplayName("QinQ double-tagged VLAN Ethernet packet unwraps both tags")
    void decodesQinQDoubleTaggedVlan() {
        byte[] frameBytes = buildQinQDoubleTaggedIpv4Tcp(10, 20, 30001, 80);
        CapturedFrame frame = new CapturedFrame(
                4L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.vlanId()).isEqualTo(10);
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV4);
        assertThat(packet.sourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.destinationIp()).isEqualTo("192.168.1.20");
        assertThat(packet.info()).contains("[VLAN 10/20]");
    }

    @Test
    @DisplayName("TCP flags extraction for representative combinations including ECE and CWR")
    void extractsTcpFlagsRepresentativeCombinations() {
        // 1. SYN only
        assertFlags((byte) 0x02, "SYN");
        // 2. SYN + ACK
        assertFlags((byte) 0x12, "SYN,ACK");
        // 3. FIN + ACK
        assertFlags((byte) 0x11, "FIN,ACK");
        // 4. RST
        assertFlags((byte) 0x04, "RST");
        // 5. PSH + ACK
        assertFlags((byte) 0x18, "PSH,ACK");
        // 6. URG
        assertFlags((byte) 0x20, "URG");
        // 7. ECE
        assertFlags((byte) 0x40, "ECE");
        // 8. CWR
        assertFlags((byte) 0x80, "CWR");
        // 9. SYN + ECE + CWR
        assertFlags((byte) 0xC2, "SYN,ECE,CWR");
        // 10. All 8 control bits: FIN(1), SYN(2), RST(4), PSH(8), ACK(16), URG(32), ECE(64), CWR(128)
        assertFlags((byte) 0xFF, "FIN,SYN,RST,PSH,ACK,URG,ECE,CWR");
    }

    @Test
    @DisplayName("IPv4 initial fragment (offset=0, MF=true) extracts transport headers and annotates fragment")
    void decodesIpv4InitialFragment() {
        // offset=0, moreFragments=true -> flags/offset = 0x2000
        byte[] frameBytes = buildEthernetIpv4Tcp(null, (short) 0x2000, (byte) 0x02, 54321, 80);
        CapturedFrame frame = new CapturedFrame(
                5L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV4);
        assertThat(packet.sourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.destinationIp()).isEqualTo("192.168.1.20");
        assertThat(packet.sourcePort()).isEqualTo(54321);
        assertThat(packet.destinationPort()).isEqualTo(80);
        assertThat(packet.tcpFlags()).isEqualTo("SYN");
        assertThat(packet.info()).contains("Fragment [offset=0, more=true]");
    }

    @Test
    @DisplayName("IPv4 non-initial fragment (offset > 0) does NOT fabricate transport ports or TCP flags")
    void decodesIpv4NonInitialFragmentWithoutTransportFabrication() {
        // offset=185 (1480 bytes), moreFragments=false -> flags/offset = 0x00B9
        byte[] frameBytes = buildEthernetIpv4Fragment(null, (short) 0x00B9, 80);
        CapturedFrame frame = new CapturedFrame(
                6L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV4);
        assertThat(packet.sourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.destinationIp()).isEqualTo("192.168.1.20");
        assertThat(packet.protocol()).isEqualTo(Protocol.OTHER);
        assertThat(packet.sourcePort()).isNull();
        assertThat(packet.destinationPort()).isNull();
        assertThat(packet.tcpFlags()).isNull();
        assertThat(packet.payloadSize()).isEqualTo(80);
        assertThat(packet.info()).contains("IPv4 Fragment [offset=1480, more=false");
    }

    @Test
    @DisplayName("IPv6 fragment header with offset=0 (initial fragment) decodes transport layer")
    void decodesIpv6InitialFragment() {
        byte[] frameBytes = buildEthernetIpv6Fragment(0, true, 44321, 80);
        CapturedFrame frame = new CapturedFrame(
                7L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV6);
        assertThat(packet.sourceIp()).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(packet.destinationIp()).isEqualTo("2001:db8:0:0:0:0:0:2");
        assertThat(packet.sourcePort()).isEqualTo(44321);
        assertThat(packet.destinationPort()).isEqualTo(80);
        assertThat(packet.info()).contains("Fragment [offset=0, more=true]");
    }

    @Test
    @DisplayName("IPv6 fragment header with offset > 0 (non-initial) preserves IPv6 without transport fabrication")
    void decodesIpv6NonInitialFragmentWithoutTransportFabrication() {
        byte[] frameBytes = buildEthernetIpv6Fragment(185, false, 44321, 80);
        CapturedFrame frame = new CapturedFrame(
                8L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isFalse();
        assertThat(packet.networkProtocol()).isEqualTo(NetworkProtocol.IPV6);
        assertThat(packet.sourceIp()).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(packet.destinationIp()).isEqualTo("2001:db8:0:0:0:0:0:2");
        assertThat(packet.protocol()).isEqualTo(Protocol.OTHER);
        assertThat(packet.sourcePort()).isNull();
        assertThat(packet.destinationPort()).isNull();
        assertThat(packet.tcpFlags()).isNull();
        assertThat(packet.info()).contains("IPv6 Fragment [offset=1480, more=false");
    }

    @Test
    @DisplayName("Truncated and malformed frames do not crash and produce malformed DecodedPacket")
    void handlesMalformedFramesSafely() {
        // Less than Ethernet header length
        byte[] truncated = new byte[]{0x00, 0x11, 0x22};
        CapturedFrame frame = new CapturedFrame(
                9L, Instant.now(), truncated, 0, truncated.length, truncated.length, 1);

        DecodedPacket packet = decoder.decode(frame);

        assertThat(packet.malformed()).isTrue();
        assertThat(packet.protocol()).isEqualTo(Protocol.OTHER);
        assertThat(packet.info()).contains("Malformed frame");

        // Unsupported link type
        CapturedFrame unsupported = new CapturedFrame(
                10L, Instant.now(), new byte[64], 0, 64, 64, 9999);
        DecodedPacket unsuppPacket = decoder.decode(unsupported);
        assertThat(unsuppPacket.malformed()).isTrue();
        assertThat(unsuppPacket.info()).contains("Unsupported link layer type 9999");
    }

    /* =========================================================================
     * Test Helpers & Synthetic Byte Array Builders
     * ========================================================================= */

    private void assertFlags(byte flagByte, String expectedFlags) {
        byte[] frameBytes = buildEthernetIpv4Tcp(null, (short) 0, flagByte, 50000, 80);
        CapturedFrame frame = new CapturedFrame(
                100L, Instant.now(), frameBytes, 0, frameBytes.length, frameBytes.length, 1);
        DecodedPacket packet = decoder.decode(frame);
        assertThat(packet.tcpFlags()).isEqualTo(expectedFlags);
    }

    private byte[] buildEthernetIpv4Tcp(
            Integer vlanId, short flagsAndOffset, byte tcpFlagByte, int srcPort, int dstPort) {

        int ethernetHeaderLen = (vlanId != null) ? 18 : 14;
        int totalLen = ethernetHeaderLen + 20 + 20; // Ethernet + IP + TCP
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        // Ethernet MACs
        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}); // Dst MAC
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb}); // Src MAC

        if (vlanId != null) {
            buf.putShort((short) 0x8100); // 802.1Q TPID
            buf.putShort((short) (vlanId & 0x0FFF)); // TCI (VLAN ID)
        }
        buf.putShort((short) 0x0800); // EtherType: IPv4

        // IPv4 Header (20 bytes)
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) 40); // Total Length
        buf.putShort((short) 0x1234);
        buf.putShort(flagsAndOffset);
        buf.put((byte) 64); // TTL
        buf.put((byte) 6);  // TCP
        buf.putShort((short) 0); // Checksum
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 10 }); // Src IP
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 20 }); // Dst IP

        // TCP Header (20 bytes)
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putInt(1000); // Seq
        buf.putInt(2000); // Ack
        buf.put((byte) 0x50); // Data offset (5 words = 20 bytes)
        buf.put(tcpFlagByte); // Flags
        buf.putShort((short) 64240); // Window
        buf.putShort((short) 0);     // Checksum
        buf.putShort((short) 0);     // Urgent pointer

        return buf.array();
    }

    private byte[] buildEthernetIpv6Tcp(Integer vlanId, int srcPort, int dstPort) {
        int ethernetHeaderLen = (vlanId != null) ? 18 : 14;
        int totalLen = ethernetHeaderLen + 40 + 20; // Ethernet + IPv6 + TCP
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});

        if (vlanId != null) {
            buf.putShort((short) 0x8100);
            buf.putShort((short) (vlanId & 0x0FFF));
        }
        buf.putShort((short) 0x86DD); // EtherType: IPv6

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000); // Version 6, TC 0, Flow 0
        buf.putShort((short) 20); // Payload length: 20 bytes TCP
        buf.put((byte) 6); // Next header: TCP
        buf.put((byte) 64); // Hop limit

        // 2001:db8::1 (16 bytes)
        buf.put(new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        // 2001:db8::2 (16 bytes)
        buf.put(new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2});

        // TCP Header (20 bytes)
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putInt(100);
        buf.putInt(0);
        buf.put((byte) 0x50);
        buf.put((byte) 0x02); // SYN
        buf.putShort((short) 8192);
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        return buf.array();
    }

    private byte[] buildQinQDoubleTaggedIpv4Tcp(int outerVid, int innerVid, int srcPort, int dstPort) {
        int totalLen = 22 + 20 + 20; // 14 Ethernet + 4 Outer VLAN + 4 Inner VLAN + 20 IP + 20 TCP
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});

        buf.putShort((short) 0x8100); // Outer 802.1Q TPID
        buf.putShort((short) (outerVid & 0x0FFF));

        buf.putShort((short) 0x8100); // Inner 802.1Q TPID
        buf.putShort((short) (innerVid & 0x0FFF));

        buf.putShort((short) 0x0800); // IPv4

        // IPv4 Header
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) 40);
        buf.putShort((short) 0x1234);
        buf.putShort((short) 0);
        buf.put((byte) 64);
        buf.put((byte) 6);
        buf.putShort((short) 0);
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 10 });
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 20 });

        // TCP Header
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putInt(100);
        buf.putInt(0);
        buf.put((byte) 0x50);
        buf.put((byte) 0x02); // SYN
        buf.putShort((short) 8192);
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        return buf.array();
    }

    private byte[] buildEthernetIpv4Fragment(Integer vlanId, short flagsAndOffset, int payloadBytes) {
        int ethLen = (vlanId != null) ? 18 : 14;
        int totalLen = ethLen + 20 + payloadBytes;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});

        if (vlanId != null) {
            buf.putShort((short) 0x8100);
            buf.putShort((short) (vlanId & 0x0FFF));
        }
        buf.putShort((short) 0x0800);

        // IPv4 Header
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) (20 + payloadBytes));
        buf.putShort((short) 0x4321); // ID
        buf.putShort(flagsAndOffset);
        buf.put((byte) 64);
        buf.put((byte) 6); // TCP protocol number
        buf.putShort((short) 0);
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 10 });
        buf.put(new byte[]{ (byte) 192, (byte) 168, 1, 20 });

        // Raw payload bytes (NOT a TCP header)
        for (int i = 0; i < payloadBytes; i++) {
            buf.put((byte) (i & 0xFF));
        }

        return buf.array();
    }

    private byte[] buildEthernetIpv6Fragment(int offsetUnits, boolean moreFragments, int srcPort, int dstPort) {
        int transportLen = (offsetUnits == 0) ? 20 : 64;
        int totalLen = 14 + 40 + 8 + transportLen; // Eth(14) + IPv6(40) + FragHeader(8) + Payload
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
        buf.putShort((short) 0x86DD); // IPv6

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000);
        buf.putShort((short) (8 + transportLen)); // Payload length
        buf.put((byte) 44); // Next header: 44 (IPv6 Fragment Header)
        buf.put((byte) 64);

        buf.put(new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        buf.put(new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2});

        // IPv6 Fragment Header (8 bytes)
        buf.put((byte) 6); // Next header: TCP
        buf.put((byte) 0); // Reserved
        short offsetAndM = (short) ((offsetUnits << 3) | (moreFragments ? 1 : 0));
        buf.putShort(offsetAndM);
        buf.putInt(0x11223344); // Identification

        if (offsetUnits == 0) {
            // Initial fragment carries TCP header
            buf.putShort((short) srcPort);
            buf.putShort((short) dstPort);
            buf.putInt(100);
            buf.putInt(0);
            buf.put((byte) 0x50);
            buf.put((byte) 0x02); // SYN
            buf.putShort((short) 8192);
            buf.putShort((short) 0);
            buf.putShort((short) 0);
        } else {
            // Non-initial fragment carries arbitrary data
            for (int i = 0; i < transportLen; i++) {
                buf.put((byte) (i & 0xFF));
            }
        }

        return buf.array();
    }
}
