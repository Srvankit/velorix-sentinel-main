package com.velorix.sentinel.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.detection.DetectionContext;
import com.velorix.sentinel.detection.DetectionLimits;
import com.velorix.sentinel.detection.PacketRecord;
import com.velorix.sentinel.detection.RuleEvaluator;
import com.velorix.sentinel.detection.ThreatCandidate;
import com.velorix.sentinel.detection.rules.DnsAnomalyRule;
import com.velorix.sentinel.detection.rules.PortScanRule;
import com.velorix.sentinel.detection.rules.SynScanRule;
import com.velorix.sentinel.entity.enums.ThreatType;
import com.velorix.sentinel.inspection.decoder.DecodedPacket;
import com.velorix.sentinel.inspection.decoder.PacketDecoder;
import com.velorix.sentinel.inspection.reader.CaptureReadResult;
import com.velorix.sentinel.inspection.reader.CaptureReaderResolver;
import com.velorix.sentinel.inspection.reader.PcapNgStreamReader;
import com.velorix.sentinel.inspection.reader.PcapStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates all 6 golden PCAP/PCAPNG capture fixtures:
 * 1. clean_traffic.pcap (benign HTTP/DNS traffic)
 * 2. syn_port_scan.pcap (Port scan / SYN scan)
 * 3. dns_tunnel_sample.pcap (Exfiltration via DNS)
 * 4. vlan_tagged_traffic.pcap (802.1Q tagged packets)
 * 5. truncated_sample.pcap (Capture cut mid-frame)
 * 6. pcapng_sample.pcapng (PCAPNG format)
 *
 * Verifies stream parsing, decoder fidelity, threat rule firing, and file size limits (&lt; 500 KB).
 */
class GoldenPcapFixturesIntegrationTest {

    private CaptureReaderResolver readerResolver;
    private PacketDecoder packetDecoder;

    @BeforeEach
    void setUp() {
        readerResolver = new CaptureReaderResolver(List.of(new PcapStreamReader(), new PcapNgStreamReader()));
        packetDecoder = new PacketDecoder();
    }

    private static DetectionContext testContext() {
        Instant now = Instant.now();
        return new DetectionContext(UUID.randomUUID(), 1000, now, now.plusSeconds(60), DetectionLimits.defaults());
    }

    private static PacketRecord toRecord(DecodedPacket p) {
        return new PacketRecord(
                p.number(),
                p.timestamp(),
                p.sourceIp(),
                p.destinationIp(),
                p.sourcePort(),
                p.destinationPort(),
                p.protocol(),
                p.networkProtocol(),
                p.packetLength(),
                p.payloadSize(),
                p.tcpFlags(),
                p.malformed());
    }

    private byte[] loadFixtureBytes(String fixtureName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/captures/" + fixtureName);
        if (is != null) {
            try (is) {
                return is.readAllBytes();
            }
        }
        return switch (fixtureName) {
            case "clean_traffic.pcap" -> GoldenPcapFixtureGenerator.buildCleanTrafficPcap();
            case "syn_port_scan.pcap" -> GoldenPcapFixtureGenerator.buildSynPortScanPcap();
            case "dns_tunnel_sample.pcap" -> GoldenPcapFixtureGenerator.buildDnsTunnelPcap();
            case "vlan_tagged_traffic.pcap" -> GoldenPcapFixtureGenerator.buildVlanTaggedPcap();
            case "truncated_sample.pcap" -> GoldenPcapFixtureGenerator.buildTruncatedPcap();
            case "pcapng_sample.pcapng" -> GoldenPcapFixtureGenerator.buildPcapNgSample();
            default -> throw new IllegalArgumentException("Unknown fixture: " + fixtureName);
        };
    }

    @Test
    @DisplayName("clean_traffic.pcap is lean (&lt; 500 KB), decodes 10 frames cleanly, and raises no threats")
    void testCleanTrafficFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("clean_traffic.pcap");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = decodeAll(bytes);
        assertThat(packets).hasSize(10);
        assertThat(packets).allMatch(p -> !p.malformed());

        // Verify benign traffic does not trigger port scan or syn scan
        DetectionContext ctx = testContext();
        PortScanRule portScanRule = new PortScanRule(15);
        SynScanRule synScanRule = new SynScanRule(20);

        RuleEvaluator portScanEval = portScanRule.newEvaluator(ctx);
        RuleEvaluator synScanEval = synScanRule.newEvaluator(ctx);

        for (DecodedPacket p : packets) {
            PacketRecord rec = toRecord(p);
            portScanEval.accept(rec);
            synScanEval.accept(rec);
        }

        assertThat(portScanEval.finish()).isEmpty();
        assertThat(synScanEval.finish()).isEmpty();
    }

    @Test
    @DisplayName("syn_port_scan.pcap is lean (&lt; 500 KB) and triggers PORT_SCAN and SYN_SCAN rules")
    void testSynPortScanFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("syn_port_scan.pcap");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = decodeAll(bytes);
        assertThat(packets).hasSize(30);

        DetectionContext ctx = testContext();
        PortScanRule portScanRule = new PortScanRule(15);
        SynScanRule synScanRule = new SynScanRule(20);

        RuleEvaluator portScanEval = portScanRule.newEvaluator(ctx);
        RuleEvaluator synScanEval = synScanRule.newEvaluator(ctx);

        for (DecodedPacket p : packets) {
            PacketRecord rec = toRecord(p);
            portScanEval.accept(rec);
            synScanEval.accept(rec);
        }

        List<ThreatCandidate> portScanThreats = portScanEval.finish();
        List<ThreatCandidate> synScanThreats = synScanEval.finish();

        assertThat(portScanThreats).isNotEmpty();
        assertThat(portScanThreats.get(0).threatType()).isEqualTo(ThreatType.PORT_SCAN);
        assertThat(synScanThreats).isNotEmpty();
        assertThat(synScanThreats.get(0).threatType()).isEqualTo(ThreatType.SYN_SCAN);
    }

    @Test
    @DisplayName("dns_tunnel_sample.pcap is lean (&lt; 500 KB) and triggers DNS_ANOMALY rule")
    void testDnsTunnelFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("dns_tunnel_sample.pcap");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = decodeAll(bytes);
        assertThat(packets).hasSize(60);

        DetectionContext ctx = testContext();
        DnsAnomalyRule dnsRule = new DnsAnomalyRule(1232);
        RuleEvaluator dnsEval = dnsRule.newEvaluator(ctx);

        for (DecodedPacket p : packets) {
            PacketRecord rec = toRecord(p);
            dnsEval.accept(rec);
        }

        List<ThreatCandidate> threats = dnsEval.finish();
        assertThat(threats).isNotEmpty();
        assertThat(threats.get(0).threatType()).isEqualTo(ThreatType.DNS_ANOMALY);
    }

    @Test
    @DisplayName("vlan_tagged_traffic.pcap is lean (&lt; 500 KB) and unwraps 802.1Q and QinQ tags")
    void testVlanTaggedTrafficFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("vlan_tagged_traffic.pcap");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = decodeAll(bytes);
        assertThat(packets).hasSize(4);

        // Frame 1: VLAN 100
        assertThat(packets.get(0).vlanId()).isEqualTo(100);
        assertThat(packets.get(0).info()).contains("[VLAN 100]");

        // Frame 2: VLAN 100
        assertThat(packets.get(1).vlanId()).isEqualTo(100);

        // Frame 3: VLAN 200
        assertThat(packets.get(2).vlanId()).isEqualTo(200);

        // Frame 4: QinQ outer 10, inner 20
        assertThat(packets.get(3).vlanId()).isEqualTo(10);
        assertThat(packets.get(3).info()).contains("[VLAN 10/20]");
    }

    @Test
    @DisplayName("truncated_sample.pcap is lean (&lt; 500 KB), yields valid frames, and sets truncated=true")
    void testTruncatedSampleFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("truncated_sample.pcap");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = new ArrayList<>();
        CaptureReadResult result = readerResolver.stream(new ByteArrayInputStream(bytes), frame -> {
            packets.add(packetDecoder.decode(frame));
            return true;
        });

        assertThat(packets).hasSize(2); // 2 complete frames before truncation
        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @DisplayName("pcapng_sample.pcapng is lean (&lt; 500 KB) and parses EPB blocks accurately")
    void testPcapNgSampleFixture() throws Exception {
        byte[] bytes = loadFixtureBytes("pcapng_sample.pcapng");
        assertThat(bytes.length).isLessThan(500_000);

        List<DecodedPacket> packets = decodeAll(bytes);
        assertThat(packets).hasSize(5);
        assertThat(packets).allMatch(p -> !p.malformed());
    }

    private List<DecodedPacket> decodeAll(byte[] captureBytes) throws Exception {
        List<DecodedPacket> packets = new ArrayList<>();
        CaptureReadResult result = readerResolver.stream(new ByteArrayInputStream(captureBytes), frame -> {
            packets.add(packetDecoder.decode(frame));
            return true;
        });
        assertThat(result.truncated()).isFalse();
        return packets;
    }
}
