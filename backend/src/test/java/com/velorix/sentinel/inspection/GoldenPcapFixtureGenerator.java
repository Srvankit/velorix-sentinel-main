package com.velorix.sentinel.inspection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generator and validator for golden PCAP / PCAPNG test fixtures.
 * Produces deterministic, lean (&lt; 50 KB) capture files under src/test/resources/captures/.
 */
public class GoldenPcapFixtureGenerator {

    private static final Path CAPTURES_DIR = Paths.get("src/test/resources/captures");

    private static byte[] ip(int a, int b, int c, int d) {
        return new byte[]{(byte) a, (byte) b, (byte) c, (byte) d};
    }

    @Test
    @DisplayName("Generate all 6 golden capture fixtures")
    void generateAllFixtures() throws IOException {
        Path targetDir = Files.exists(CAPTURES_DIR)
                ? CAPTURES_DIR
                : Paths.get("backend/src/test/resources/captures");

        Files.createDirectories(targetDir);

        byte[] cleanTraffic = buildCleanTrafficPcap();
        byte[] synPortScan = buildSynPortScanPcap();
        byte[] dnsTunnel = buildDnsTunnelPcap();
        byte[] vlanTraffic = buildVlanTaggedPcap();
        byte[] truncatedSample = buildTruncatedPcap();
        byte[] pcapNgSample = buildPcapNgSample();

        Files.write(targetDir.resolve("clean_traffic.pcap"), cleanTraffic);
        Files.write(targetDir.resolve("syn_port_scan.pcap"), synPortScan);
        Files.write(targetDir.resolve("dns_tunnel_sample.pcap"), dnsTunnel);
        Files.write(targetDir.resolve("vlan_tagged_traffic.pcap"), vlanTraffic);
        Files.write(targetDir.resolve("truncated_sample.pcap"), truncatedSample);
        Files.write(targetDir.resolve("pcapng_sample.pcapng"), pcapNgSample);

        assertThat(cleanTraffic.length).isLessThan(500_000);
        assertThat(synPortScan.length).isLessThan(500_000);
        assertThat(dnsTunnel.length).isLessThan(500_000);
        assertThat(vlanTraffic.length).isLessThan(500_000);
        assertThat(truncatedSample.length).isLessThan(500_000);
        assertThat(pcapNgSample.length).isLessThan(500_000);
    }

    public static byte[] buildCleanTrafficPcap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePcapGlobalHeader(out);

        int sec = 1710000000;
        byte[] clientIp = ip(192, 168, 1, 50);
        byte[] serverIp = ip(93, 184, 216, 34);
        byte[] dnsIp = ip(8, 8, 8, 8);

        // 1. DNS Query: client -> DNS
        byte[] dnsQuery = buildDnsPacket(clientIp, dnsIp, 53123, 53, "example.com", false);
        writePcapRecord(out, sec, 100_000, dnsQuery);

        // 2. DNS Response: DNS -> client
        byte[] dnsResp = buildDnsPacket(dnsIp, clientIp, 53, 53123, "example.com", true);
        writePcapRecord(out, sec, 150_000, dnsResp);

        // 3. TCP Handshake: SYN
        byte[] syn = buildTcpPacket(clientIp, serverIp, 49152, 80, 1000, 0, (byte) 0x02, new byte[0]);
        writePcapRecord(out, sec + 1, 200_000, syn);

        // 4. TCP Handshake: SYN-ACK
        byte[] synAck = buildTcpPacket(serverIp, clientIp, 80, 49152, 5000, 1001, (byte) 0x12, new byte[0]);
        writePcapRecord(out, sec + 1, 250_000, synAck);

        // 5. TCP Handshake: ACK
        byte[] ack = buildTcpPacket(clientIp, serverIp, 49152, 80, 1001, 5001, (byte) 0x10, new byte[0]);
        writePcapRecord(out, sec + 1, 260_000, ack);

        // 6. HTTP GET
        byte[] httpReq = "GET /index.html HTTP/1.1\r\nHost: example.com\r\nUser-Agent: curl/8.4.0\r\nAccept: */*\r\n\r\n".getBytes();
        byte[] httpPacket = buildTcpPacket(clientIp, serverIp, 49152, 80, 1001, 5001, (byte) 0x18, httpReq);
        writePcapRecord(out, sec + 1, 300_000, httpPacket);

        // 7. HTTP 200 Response
        byte[] httpResp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 13\r\n\r\nHello, World!".getBytes();
        byte[] respPacket = buildTcpPacket(serverIp, clientIp, 80, 49152, 5001, 1001 + httpReq.length, (byte) 0x18, httpResp);
        writePcapRecord(out, sec + 1, 350_000, respPacket);

        // 8. Teardown: FIN-ACK from client
        byte[] finClient = buildTcpPacket(clientIp, serverIp, 49152, 80, 1001 + httpReq.length, 5001 + httpResp.length, (byte) 0x11, new byte[0]);
        writePcapRecord(out, sec + 2, 400_000, finClient);

        // 9. Teardown: FIN-ACK from server
        byte[] finServer = buildTcpPacket(serverIp, clientIp, 80, 49152, 5001 + httpResp.length, 1001 + httpReq.length + 1, (byte) 0x11, new byte[0]);
        writePcapRecord(out, sec + 2, 420_000, finServer);

        // 10. Final ACK from client
        byte[] finalAck = buildTcpPacket(clientIp, serverIp, 49152, 80, 1001 + httpReq.length + 1, 5001 + httpResp.length + 1, (byte) 0x10, new byte[0]);
        writePcapRecord(out, sec + 2, 430_000, finalAck);

        return out.toByteArray();
    }

    public static byte[] buildSynPortScanPcap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePcapGlobalHeader(out);

        int sec = 1710001000;
        byte[] srcIp = ip(192, 168, 1, 200);
        byte[] dstIp = ip(10, 0, 0, 10);

        int[] targetPorts = {
                21, 22, 23, 25, 53, 80, 110, 135, 139, 143,
                443, 445, 993, 995, 1433, 1521, 3306, 3389, 5432, 5900,
                6379, 8000, 8080, 8443, 8888, 9000, 9090, 9200, 27017, 50000
        };

        for (int i = 0; i < targetPorts.length; i++) {
            int port = targetPorts[i];
            byte[] syn = buildTcpPacket(
                    srcIp, dstIp, 40000 + i, port, 10000 + i, 0, (byte) 0x02, new byte[0]);
            writePcapRecord(out, sec, i * 10_000, syn);
        }

        return out.toByteArray();
    }

    public static byte[] buildDnsTunnelPcap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePcapGlobalHeader(out);

        int sec = 1710002000;
        byte[] srcIp = ip(192, 168, 1, 150);
        byte[] dnsServer = ip(8, 8, 8, 8);

        // 60 total DNS queries. 20 of them have payload size > 1232 bytes (tunnelling pattern).
        for (int i = 0; i < 60; i++) {
            boolean isLarge = (i % 3 == 0); // 20 out of 60 = 33.3% > 20%
            int payloadSize = isLarge ? 1300 : 80;
            byte[] dnsPayload = new byte[payloadSize];
            Arrays.fill(dnsPayload, (byte) ('a' + (i % 26)));

            byte[] packet = buildUdpPacket(srcIp, dnsServer, 50000 + i, 53, dnsPayload);
            writePcapRecord(out, sec + (i / 10), (i % 10) * 100_000, packet);
        }

        return out.toByteArray();
    }

    public static byte[] buildVlanTaggedPcap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePcapGlobalHeader(out);

        int sec = 1710003000;
        byte[] srcIp = ip(10, 10, 10, 1);
        byte[] dstIp = ip(10, 10, 10, 2);

        // Frame 1: VLAN 100 tagged IPv4 TCP SYN
        byte[] vlan100 = buildVlanEthernetFrame(100, null, srcIp, dstIp, 40001, 80, (byte) 0x02);
        writePcapRecord(out, sec, 100_000, vlan100);

        // Frame 2: VLAN 100 tagged IPv4 TCP SYN-ACK
        byte[] vlan100Ack = buildVlanEthernetFrame(100, null, dstIp, srcIp, 80, 40001, (byte) 0x12);
        writePcapRecord(out, sec, 200_000, vlan100Ack);

        // Frame 3: VLAN 200 tagged IPv4 TCP SYN
        byte[] vlan200 = buildVlanEthernetFrame(200, null, srcIp, dstIp, 40002, 443, (byte) 0x02);
        writePcapRecord(out, sec, 300_000, vlan200);

        // Frame 4: QinQ Double tagged (Outer 10, Inner 20)
        byte[] qinq = buildVlanEthernetFrame(10, 20, srcIp, dstIp, 40003, 8080, (byte) 0x02);
        writePcapRecord(out, sec, 400_000, qinq);

        return out.toByteArray();
    }

    public static byte[] buildTruncatedPcap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePcapGlobalHeader(out);

        int sec = 1710004000;
        byte[] srcIp = ip(192, 168, 1, 10);
        byte[] dstIp = ip(192, 168, 1, 20);

        // Frame 1: Valid TCP SYN
        byte[] f1 = buildTcpPacket(srcIp, dstIp, 10001, 80, 1, 0, (byte) 0x02, new byte[0]);
        writePcapRecord(out, sec, 100_000, f1);

        // Frame 2: Valid TCP SYN-ACK
        byte[] f2 = buildTcpPacket(dstIp, srcIp, 80, 10001, 2, 2, (byte) 0x12, new byte[0]);
        writePcapRecord(out, sec, 200_000, f2);

        // Frame 3: Record header says 100 bytes incl_len, but write only 20 bytes of body!
        ByteBuffer recHeader = ByteBuffer.allocate(16);
        recHeader.putInt(sec);
        recHeader.putInt(300_000);
        recHeader.putInt(100); // incl_len
        recHeader.putInt(100); // orig_len
        try {
            out.write(recHeader.array());
            out.write(new byte[20]); // partial 20 bytes instead of 100
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return out.toByteArray();
    }

    public static byte[] buildPcapNgSample() {
        int epbCount = 5;
        int epbSize = 88;
        ByteBuffer buf = ByteBuffer.allocate(28 + 20 + epbCount * epbSize);

        // 1. Section Header Block (SHB) - 28 bytes
        buf.putInt(0x0A0D0D0A);         // Block Type
        buf.putInt(28);                 // Total Length
        buf.putInt(0x1A2B3C4D);         // Byte Order Magic
        buf.putShort((short) 1);        // Major Version
        buf.putShort((short) 0);        // Minor Version
        buf.putLong(-1L);               // Section Length
        buf.putInt(28);                 // Total Length trailer

        // 2. Interface Description Block (IDB) - 20 bytes
        buf.putInt(0x00000001);         // Block Type
        buf.putInt(20);                 // Total Length
        buf.putShort((short) 1);        // LinkType = LINKTYPE_ETHERNET
        buf.putShort((short) 0);        // Reserved
        buf.putInt(65535);              // SnapLen
        buf.putInt(20);                 // Total Length trailer

        // 3. Enhanced Packet Blocks (EPB)
        for (int i = 1; i <= epbCount; i++) {
            buf.putInt(0x00000006);     // Block Type
            buf.putInt(epbSize);        // Total Length
            buf.putInt(0);              // Interface ID
            buf.putInt(0);              // Timestamp High
            buf.putInt(i * 1_000_000);  // Timestamp Low
            buf.putInt(54);             // Captured Length
            buf.putInt(54);             // Original Length

            // Ethernet (14 bytes)
            buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
            buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
            buf.putShort((short) 0x0800);

            // IPv4 (20 bytes)
            buf.put((byte) 0x45);
            buf.put((byte) 0x00);
            buf.putShort((short) 40);
            buf.putShort((short) (0x3000 + i));
            buf.putShort((short) 0x4000);
            buf.put((byte) 64);
            buf.put((byte) 6);
            buf.putShort((short) 0);
            buf.put(ip(10, 2, 0, i));
            buf.put(ip(10, 2, 0, 99));

            // TCP (20 bytes)
            buf.putShort((short) (45000 + i));
            buf.putShort((short) 80);
            buf.putInt(i);
            buf.putInt(0);
            buf.putShort((short) 0x5002); // SYN
            buf.putShort((short) 8192);
            buf.putShort((short) 0);
            buf.putShort((short) 0);

            // Padding (2 bytes)
            buf.put((byte) 0);
            buf.put((byte) 0);

            // Total Length trailer (4 bytes)
            buf.putInt(epbSize);
        }

        return buf.array();
    }

    // --- Low-level packet builder helpers ---

    private static void writePcapGlobalHeader(ByteArrayOutputStream out) {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putInt(0xa1b2c3d4);          // Magic
        buf.putShort((short) 2);         // Major
        buf.putShort((short) 4);         // Minor
        buf.putInt(0);                   // Thiszone
        buf.putInt(0);                   // Sigfigs
        buf.putInt(65535);               // Snaplen
        buf.putInt(1);                   // LinkType = LINKTYPE_ETHERNET
        try {
            out.write(buf.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writePcapRecord(ByteArrayOutputStream out, int sec, int usec, byte[] packetData) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putInt(sec);
        buf.putInt(usec);
        buf.putInt(packetData.length);
        buf.putInt(packetData.length);
        try {
            out.write(buf.array());
            out.write(packetData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] buildTcpPacket(
            byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, int seq, int ack, byte flags, byte[] payload) {
        int tcpLen = 20 + payload.length;
        int ipLen = 20 + tcpLen;
        int totalLen = 14 + ipLen;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        // Ethernet (14 bytes)
        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
        buf.putShort((short) 0x0800); // IPv4

        // IPv4 Header (20 bytes)
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) ipLen);
        buf.putShort((short) 0x1234);
        buf.putShort((short) 0x4000); // DF
        buf.put((byte) 64);           // TTL
        buf.put((byte) 6);            // Protocol TCP
        buf.putShort((short) 0);      // Checksum
        buf.put(srcIp);
        buf.put(dstIp);

        // TCP Header (20 bytes)
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putInt(seq);
        buf.putInt(ack);
        buf.putShort((short) (0x5000 | (flags & 0xFF))); // Offset 5, flags
        buf.putShort((short) 8192);   // Window
        buf.putShort((short) 0);      // Checksum
        buf.putShort((short) 0);      // Urgent pointer

        if (payload.length > 0) {
            buf.put(payload);
        }

        return buf.array();
    }

    public static byte[] buildUdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        int udpLen = 8 + payload.length;
        int ipLen = 20 + udpLen;
        int totalLen = 14 + ipLen;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        // Ethernet (14 bytes)
        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
        buf.putShort((short) 0x0800); // IPv4

        // IPv4 (20 bytes)
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) ipLen);
        buf.putShort((short) 0x5678);
        buf.putShort((short) 0x0000);
        buf.put((byte) 64);
        buf.put((byte) 17); // UDP
        buf.putShort((short) 0);
        buf.put(srcIp);
        buf.put(dstIp);

        // UDP (8 bytes)
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putShort((short) udpLen);
        buf.putShort((short) 0); // Checksum

        buf.put(payload);

        return buf.array();
    }

    public static byte[] buildDnsPacket(
            byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, String domain, boolean isResponse) {
        // Build minimal standard DNS payload
        ByteArrayOutputStream dnsOut = new ByteArrayOutputStream();
        ByteBuffer h = ByteBuffer.allocate(12);
        h.putShort((short) 0x1234); // Transaction ID
        h.putShort((short) (isResponse ? 0x8180 : 0x0100)); // Standard query or response
        h.putShort((short) 1);      // Questions
        h.putShort((short) (isResponse ? 1 : 0)); // Answer RRs
        h.putShort((short) 0);      // Authority RRs
        h.putShort((short) 0);      // Additional RRs
        dnsOut.writeBytes(h.array());

        // Question: label format
        for (String label : domain.split("\\.")) {
            dnsOut.write((byte) label.length());
            dnsOut.writeBytes(label.getBytes());
        }
        dnsOut.write((byte) 0); // Null terminator
        ByteBuffer qtype = ByteBuffer.allocate(4);
        qtype.putShort((short) 1); // Type A
        qtype.putShort((short) 1); // Class IN
        dnsOut.writeBytes(qtype.array());

        if (isResponse) {
            // Answer: pointer to domain offset 12
            ByteBuffer ans = ByteBuffer.allocate(16);
            ans.putShort((short) 0xC00C); // Compression pointer
            ans.putShort((short) 1);      // Type A
            ans.putShort((short) 1);      // Class IN
            ans.putInt(300);              // TTL
            ans.putShort((short) 4);      // Data length
            ans.put(ip(93, 184, 216, 34)); // IP
            dnsOut.writeBytes(ans.array());
        }

        return buildUdpPacket(srcIp, dstIp, srcPort, dstPort, dnsOut.toByteArray());
    }

    public static byte[] buildVlanEthernetFrame(
            int outerVlan, Integer innerVlan, byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte flags) {
        int vlanHeaders = (innerVlan != null) ? 8 : 4;
        int ipLen = 40; // 20 IP + 20 TCP
        int totalLen = 14 + vlanHeaders + ipLen;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        // Ethernet MACs
        buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
        buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});

        // Outer VLAN
        buf.putShort((short) 0x8100);
        buf.putShort((short) (outerVlan & 0x0FFF));

        // Inner VLAN (QinQ)
        if (innerVlan != null) {
            buf.putShort((short) 0x8100);
            buf.putShort((short) (innerVlan & 0x0FFF));
        }

        // Encapsulated EtherType = IPv4
        buf.putShort((short) 0x0800);

        // IPv4 (20 bytes)
        buf.put((byte) 0x45);
        buf.put((byte) 0x00);
        buf.putShort((short) ipLen);
        buf.putShort((short) 0x9999);
        buf.putShort((short) 0x4000);
        buf.put((byte) 64);
        buf.put((byte) 6); // TCP
        buf.putShort((short) 0);
        buf.put(srcIp);
        buf.put(dstIp);

        // TCP (20 bytes)
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putInt(1);
        buf.putInt(0);
        buf.putShort((short) (0x5000 | (flags & 0xFF)));
        buf.putShort((short) 8192);
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        return buf.array();
    }
}
