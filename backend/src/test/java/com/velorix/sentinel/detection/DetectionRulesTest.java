package com.velorix.sentinel.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.detection.rules.AbnormalPacketSizeRule;
import com.velorix.sentinel.detection.rules.BeaconingRule;
import com.velorix.sentinel.detection.rules.BruteForceRule;
import com.velorix.sentinel.detection.rules.DataExfiltrationRule;
import com.velorix.sentinel.detection.rules.DdosRule;
import com.velorix.sentinel.detection.rules.DnsAnomalyRule;
import com.velorix.sentinel.detection.rules.IcmpFloodRule;
import com.velorix.sentinel.detection.rules.PortScanRule;
import com.velorix.sentinel.detection.rules.RepeatedConnectionRule;
import com.velorix.sentinel.detection.rules.SuspiciousExternalCommRule;
import com.velorix.sentinel.detection.rules.SynScanRule;
import com.velorix.sentinel.detection.rules.UdpScanRule;
import com.velorix.sentinel.detection.rules.UnknownProtocolRule;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural and boundary validation tests for all 13 detection rules.
 *
 * <p>Each test asserts positive detection on representative attack patterns,
 * non-detection on benign traffic, threshold boundaries immediately below and at threshold,
 * and edge case resilience (empty captures, malformed frames, unknown fields).</p>
 */
class DetectionRulesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static DetectionContext context() {
        return new DetectionContext(UUID.randomUUID(), 10_000, T0, T0.plusSeconds(600),
                DetectionLimits.defaults());
    }

    private static PacketRecord tcp(long number, long offsetMs, String src, String dst, int dstPort, String flags) {
        return new PacketRecord(number, T0.plusMillis(offsetMs), src, dst, 40000 + (int) (number % 1000),
                dstPort, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, flags, false);
    }

    private static List<ThreatCandidate> run(DetectionRule rule, List<PacketRecord> packets) {
        RuleEvaluator evaluator = rule.newEvaluator(context());
        packets.forEach(evaluator::accept);
        return evaluator.finish();
    }

    /* =========================================================================
     * 1. PortScanRule
     * ========================================================================= */
    @Test
    @DisplayName("PortScanRule: fires on >= 15 distinct ports, stays quiet on <= 14 and busy single service")
    void portScan() {
        PortScanRule rule = new PortScanRule();

        // 1. Boundary below threshold: 14 distinct ports -> quiet
        List<PacketRecord> belowThreshold = new ArrayList<>();
        for (int port = 1; port <= 14; port++) {
            belowThreshold.add(tcp(port, port * 10L, "10.0.0.5", "10.0.0.9", port, "SYN"));
        }
        assertThat(run(rule, belowThreshold)).isEmpty();

        // 2. Boundary at threshold: 15 distinct ports -> fires
        List<PacketRecord> atThreshold = new ArrayList<>(belowThreshold);
        atThreshold.add(tcp(15, 150L, "10.0.0.5", "10.0.0.9", 15, "SYN"));
        List<ThreatCandidate> atFindings = run(rule, atThreshold);
        assertThat(atFindings).hasSize(1);
        assertThat(atFindings.get(0).threatType()).isEqualTo(ThreatType.PORT_SCAN);
        assertThat(atFindings.get(0).sourceIp()).isEqualTo("10.0.0.5");
        assertThat(atFindings.get(0).evidence()).contains("distinctPorts=15");

        // 3. Busy single service: 500 connections to port 443 -> quiet
        List<PacketRecord> browsing = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            browsing.add(tcp(i, i * 10L, "10.0.0.5", "10.0.0.9", 443, "SYN,ACK"));
        }
        assertThat(run(rule, browsing)).isEmpty();
    }

    /* =========================================================================
     * 2. SynScanRule & TCP SYN classification
     * ========================================================================= */
    @Test
    @DisplayName("PacketRecord.isSynOnly: strict TCP SYN classification requiring transport evidence and clean flags")
    void packetRecordSynOnly() {
        // Protocol label alone without flags can NEVER classify a packet as SYN-only
        PacketRecord httpNoFlags = new PacketRecord(1, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.HTTP, NetworkProtocol.IPV4, 100, 40, null, false);
        assertThat(httpNoFlags.isSynOnly()).isFalse();

        PacketRecord httpsNoFlags = new PacketRecord(2, T0, "10.0.0.1", "10.0.0.2", 50000, 443,
                Protocol.HTTPS, NetworkProtocol.IPV4, 100, 40, null, false);
        assertThat(httpsNoFlags.isSynOnly()).isFalse();

        // Valid TCP SYN packet
        PacketRecord tcpSyn = new PacketRecord(3, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN", false);
        assertThat(tcpSyn.isSynOnly()).isTrue();

        // HTTP protocol label with SYN flag and valid ports
        PacketRecord httpSyn = new PacketRecord(4, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, "SYN", false);
        assertThat(httpSyn.isSynOnly()).isTrue();

        // SYN-ACK is not SYN-only
        PacketRecord synAck = new PacketRecord(5, T0, "10.0.0.2", "10.0.0.1", 80, 50000,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN,ACK", false);
        assertThat(synAck.isSynOnly()).isFalse();

        // ACK is not SYN-only
        PacketRecord ack = new PacketRecord(6, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "ACK", false);
        assertThat(ack.isSynOnly()).isFalse();

        // RST is not SYN-only
        PacketRecord rst = new PacketRecord(7, T0, "10.0.0.2", "10.0.0.1", 80, 50000,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "RST", false);
        assertThat(rst.isSynOnly()).isFalse();

        // SYN,RST is not SYN-only
        PacketRecord synRst = new PacketRecord(8, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN,RST", false);
        assertThat(synRst.isSynOnly()).isFalse();

        // SYN,FIN is not SYN-only
        PacketRecord synFin = new PacketRecord(9, T0, "10.0.0.1", "10.0.0.2", 50000, 80,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN,FIN", false);
        assertThat(synFin.isSynOnly()).isFalse();

        // Missing ports cannot be SYN-only
        PacketRecord missingPorts = new PacketRecord(10, T0, "10.0.0.1", "10.0.0.2", null, null,
                Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN", false);
        assertThat(missingPorts.isSynOnly()).isFalse();

        // Non-initial fragment (offset > 0, protocol=OTHER, flags=null) cannot be SYN-only
        PacketRecord nonInitialFragment = new PacketRecord(11, T0, "10.0.0.1", "10.0.0.2", null, null,
                Protocol.OTHER, NetworkProtocol.IPV4, 1500, 1480, null, false);
        assertThat(nonInitialFragment.isSynOnly()).isFalse();
    }

    @Test
    @DisplayName("SynScanRule: fires on >= 20 half-open SYNs, detects HTTP SYN probes, stays quiet on completed handshakes")
    void synScan() {
        SynScanRule rule = new SynScanRule();

        // 1. Boundary below threshold: 19 uncompleted SYNs -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 19; i++) {
            below.add(tcp(i, i * 5L, "10.0.0.5", "10.0.0.9", 1000 + i, "SYN"));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 20 uncompleted SYNs -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(tcp(20, 100L, "10.0.0.5", "10.0.0.9", 1020, "SYN"));
        List<ThreatCandidate> atFindings = run(rule, at);
        assertThat(atFindings).hasSize(1);
        assertThat(atFindings.get(0).threatType()).isEqualTo(ThreatType.SYN_SCAN);

        // 3. SYN scan against HTTP port 80 (protocol resolved as Protocol.HTTP)
        List<PacketRecord> httpScan = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            httpScan.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.5", "10.0.0.9",
                    50000 + i, 80, Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, "SYN", false));
        }
        List<ThreatCandidate> httpFindings = run(rule, httpScan);
        assertThat(httpFindings).hasSize(1);
        assertThat(httpFindings.get(0).threatType()).isEqualTo(ThreatType.SYN_SCAN);

        // 4. Completed handshakes: SYN followed by ACK -> quiet
        List<PacketRecord> completed = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            completed.add(tcp(i * 2L, i * 10L, "10.0.0.5", "10.0.0.9", 443, "SYN"));
            completed.add(tcp(i * 2L + 1, i * 10L + 1, "10.0.0.9", "10.0.0.5", 40000, "SYN,ACK"));
        }
        assertThat(run(rule, completed)).isEmpty();

        // 5. Stealth SYN scan against closed ports returning RST,ACK (must NOT count as completed handshakes)
        List<PacketRecord> closedPortScan = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            closedPortScan.add(tcp(i * 2L, i * 10L, "10.0.0.5", "10.0.0.9", 2000 + i, "SYN"));
            closedPortScan.add(tcp(i * 2L + 1, i * 10L + 1, "10.0.0.9", "10.0.0.5", 40000, "RST,ACK"));
        }
        List<ThreatCandidate> closedPortFindings = run(rule, closedPortScan);
        assertThat(closedPortFindings).hasSize(1);
        assertThat(closedPortFindings.get(0).threatType()).isEqualTo(ThreatType.SYN_SCAN);
        assertThat(closedPortFindings.get(0).evidence()).contains("resetsOrFins=25");
        assertThat(closedPortFindings.get(0).evidence()).contains("acksReceived=0");
    }

    /* =========================================================================
     * 3. IcmpFloodRule
     * ========================================================================= */
    @Test
    @DisplayName("IcmpFloodRule: fires on >= 100 packets at >= 50 pkts/s, ignores slow ping sweep")
    void icmpFlood() {
        IcmpFloodRule rule = new IcmpFloodRule();

        // 1. Boundary below packet threshold: 99 packets at 100 pkts/s -> quiet
        List<PacketRecord> belowCount = new ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            belowCount.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.5", "10.0.0.9", null, null,
                    Protocol.ICMP, NetworkProtocol.IPV4, 98, 56, null, false));
        }
        assertThat(run(rule, belowCount)).isEmpty();

        // 2. Boundary at count threshold (100) and rate (100 pkts in 1s = 100 pkts/s >= 50) -> fires
        List<PacketRecord> atThreshold = new ArrayList<>(belowCount);
        atThreshold.add(new PacketRecord(100, T0.plusMillis(1000L), "10.0.0.5", "10.0.0.9", null, null,
                Protocol.ICMP, NetworkProtocol.IPV4, 98, 56, null, false));
        List<ThreatCandidate> findings = run(rule, atThreshold);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.ICMP_FLOOD);

        // 3. Slow ping sweep: 300 pings spaced over 300 seconds (rate = 1.0 pkts/s < 50) -> quiet
        List<PacketRecord> heartbeat = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            heartbeat.add(new PacketRecord(i, T0.plusSeconds(i), "10.0.0.5", "10.0.0.9", null, null,
                    Protocol.ICMP, NetworkProtocol.IPV4, 98, 56, null, false));
        }
        assertThat(run(rule, heartbeat)).isEmpty();
    }

    /* =========================================================================
     * 4. BruteForceRule
     * ========================================================================= */
    @Test
    @DisplayName("BruteForceRule: fires on rapid repeated auth attempts, mitigates false alarms on slow periodic admin logins")
    void bruteForce() {
        BruteForceRule rule = new BruteForceRule();

        // 1. Boundary below threshold: 29 rapid attempts to SSH -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 29; i++) {
            below.add(tcp(i, i * 100L, "203.0.113.7", "10.0.0.9", 22, "SYN"));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 30 rapid attempts to SSH (in 3 seconds, rate = 10 pkts/s) -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(tcp(30, 3000L, "203.0.113.7", "10.0.0.9", 22, "SYN"));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.BRUTE_FORCE);
        assertThat(findings.get(0).description()).contains("SSH");

        // 3. Non-auth port: 50 attempts to port 80 -> quiet
        List<PacketRecord> web = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            web.add(tcp(i, i * 100L, "203.0.113.7", "10.0.0.9", 80, "SYN"));
        }
        assertThat(run(rule, web)).isEmpty();

        // 4. False positive mitigation: 30 logins spread across 2 hours (span = 7200s, rate = 0.004 pkts/s) -> quiet
        List<PacketRecord> slowAdmin = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            slowAdmin.add(tcp(i, i * 240_000L, "203.0.113.7", "10.0.0.9", 22, "SYN"));
        }
        assertThat(run(rule, slowAdmin)).isEmpty();
    }

    /* =========================================================================
     * 5. BeaconingRule
     * ========================================================================= */
    @Test
    @DisplayName("BeaconingRule: fires on low-jitter C2 intervals, stays quiet on human browsing and internal-only traffic")
    void beaconing() {
        BeaconingRule rule = new BeaconingRule();

        // 1. Boundary below interval count: 9 intervals (10 packets) -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            below.add(tcp(i, i * 30_000L, "192.168.1.20", "198.51.100.4", 8443, "PSH,ACK"));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at interval count: 10 intervals (11 packets, 30s gap) -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(tcp(11, 11 * 30_000L, "192.168.1.20", "198.51.100.4", 8443, "PSH,ACK"));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.BEACONING);

        // 3. Human browsing jitter (> 20% variation) -> quiet
        List<PacketRecord> human = new ArrayList<>();
        long offset = 0;
        long[] jitter = {1_000, 47_000, 3_000, 91_000, 12_000, 5_000, 130_000, 2_000, 60_000, 8_000,
                25_000, 4_000, 110_000, 7_000, 33_000};
        for (int i = 0; i < jitter.length; i++) {
            offset += jitter[i];
            human.add(tcp(i + 1, offset, "192.168.1.20", "198.51.100.4", 443, "PSH,ACK"));
        }
        assertThat(run(rule, human)).isEmpty();

        // 4. Internal-to-internal traffic (both RFC1918) -> quiet
        List<PacketRecord> internalOnly = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            internalOnly.add(tcp(i, i * 30_000L, "192.168.1.20", "192.168.1.50", 8443, "PSH,ACK"));
        }
        assertThat(run(rule, internalOnly)).isEmpty();
    }

    /* =========================================================================
     * 6. AbnormalPacketSizeRule
     * ========================================================================= */
    @Test
    @DisplayName("AbnormalPacketSizeRule: fires on oversized frames and malformed frames exceeding ratio")
    void abnormalPacketSize() {
        AbnormalPacketSizeRule rule = new AbnormalPacketSizeRule();

        // 1. Boundary below oversized threshold: 4 frames > 9000 bytes -> quiet
        List<PacketRecord> belowOversized = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            belowOversized.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    1234, 80, Protocol.TCP, NetworkProtocol.IPV4, 9500, 9460, null, false));
        }
        assertThat(run(rule, belowOversized)).isEmpty();

        // 2. Boundary at oversized threshold: 5 frames > 9000 bytes -> fires
        List<PacketRecord> atOversized = new ArrayList<>(belowOversized);
        atOversized.add(new PacketRecord(5, T0.plusMillis(50L), "10.0.0.1", "10.0.0.2",
                1234, 80, Protocol.TCP, NetworkProtocol.IPV4, 9500, 9460, null, false));
        List<ThreatCandidate> oversizedFindings = run(rule, atOversized);
        assertThat(oversizedFindings).hasSize(1);
        assertThat(oversizedFindings.get(0).threatType()).isEqualTo(ThreatType.ABNORMAL_PACKET_SIZE);

        // 3. Malformed frames: 5 malformed out of 200 (2.5% < 5% threshold) -> quiet
        List<PacketRecord> belowMalformedRatio = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            boolean malformed = i <= 5;
            belowMalformedRatio.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    1234, 80, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, null, malformed));
        }
        assertThat(run(rule, belowMalformedRatio)).isEmpty();

        // 4. Malformed frames: 5 malformed out of 50 (10% >= 5% threshold) -> fires
        List<PacketRecord> atMalformedRatio = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            boolean malformed = i <= 5;
            atMalformedRatio.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    1234, 80, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, null, malformed));
        }
        List<ThreatCandidate> malformedFindings = run(rule, atMalformedRatio);
        assertThat(malformedFindings).hasSize(1);
        assertThat(malformedFindings.get(0).threatType()).isEqualTo(ThreatType.ABNORMAL_PACKET_SIZE);
    }

    /* =========================================================================
     * 7. DataExfiltrationRule
     * ========================================================================= */
    @Test
    @DisplayName("DataExfiltrationRule: fires on >= 10 MB outbound to single external IP, quiet on inbound/internal")
    void dataExfiltration() {
        DataExfiltrationRule rule = new DataExfiltrationRule();

        // 1. Boundary below threshold: 9.9 MB outbound (99 packets of 104,858 bytes = 10.38 MB < 10.485 MB) -> quiet
        int chunkSize = 104_858;
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            below.add(new PacketRecord(i, T0.plusMillis(i * 10L), "192.168.1.100", "203.0.113.50",
                    45000, 443, Protocol.HTTPS, NetworkProtocol.IPV4, chunkSize + 40, chunkSize, null, false));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 100 packets of 104,858 bytes = 10,485,800 bytes >= 10 MB (10,485,760) -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(new PacketRecord(100, T0.plusMillis(1000L), "192.168.1.100", "203.0.113.50",
                45000, 443, Protocol.HTTPS, NetworkProtocol.IPV4, chunkSize + 40, chunkSize, null, false));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.DATA_EXFILTRATION);
        assertThat(findings.get(0).severity()).isEqualTo(ThreatSeverity.HIGH);
        assertThat(findings.get(0).description()).contains("heuristic indicator of potential outbound exfiltration");

        // 3. Inbound transfer: 20 MB from external to internal -> quiet
        List<PacketRecord> inbound = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            inbound.add(new PacketRecord(i, T0.plusMillis(i * 10L), "203.0.113.50", "192.168.1.100",
                    443, 45000, Protocol.HTTPS, NetworkProtocol.IPV4, chunkSize + 40, chunkSize, null, false));
        }
        assertThat(run(rule, inbound)).isEmpty();

        // 4. Critical escalation: 50 MB outbound -> escalates to CRITICAL severity
        List<PacketRecord> severe = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            severe.add(new PacketRecord(i, T0.plusMillis(i * 10L), "192.168.1.100", "203.0.113.50",
                    45000, 443, Protocol.HTTPS, NetworkProtocol.IPV4, chunkSize + 40, chunkSize, null, false));
        }
        List<ThreatCandidate> severeFindings = run(rule, severe);
        assertThat(severeFindings).hasSize(1);
        assertThat(severeFindings.get(0).severity()).isEqualTo(ThreatSeverity.CRITICAL);

        // 5. Configurable threshold fallback: non-positive value defaults safely to 10 MB
        DataExfiltrationRule fallbackRule = new DataExfiltrationRule(0);
        assertThat(run(fallbackRule, below)).isEmpty();
    }

    /* =========================================================================
     * 8. DdosRule
     * ========================================================================= */
    @Test
    @DisplayName("DdosRule: fires on >= 500 pkts with >= 50 sources or >= 200 pkts/s, protects against '-' unclassified target")
    void ddos() {
        DdosRule rule = new DdosRule();

        // 1. Boundary below min packets: 499 packets from 60 sources -> quiet
        List<PacketRecord> belowPackets = new ArrayList<>();
        for (int i = 1; i <= 499; i++) {
            String src = "10.0.1." + (i % 60 + 1);
            belowPackets.add(new PacketRecord(i, T0.plusMillis(i * 100L), src, "192.168.1.5",
                    40000, 80, Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        assertThat(run(rule, belowPackets)).isEmpty();

        // 2. Boundary at min packets: 500 packets from 50 sources (distributed surge) -> fires
        List<PacketRecord> distributed = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            String src = "10.0.1." + (i % 50 + 1);
            distributed.add(new PacketRecord(i, T0.plusMillis(i * 100L), src, "192.168.1.5",
                    40000, 80, Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        List<ThreatCandidate> distFindings = run(rule, distributed);
        assertThat(distFindings).hasSize(1);
        assertThat(distFindings.get(0).threatType()).isEqualTo(ThreatType.DDOS);
        assertThat(distFindings.get(0).severity()).isEqualTo(ThreatSeverity.HIGH);

        // 3. Volumetric flood: 500 packets in 1 second from 1 source (rate 500 > 200) -> fires
        List<PacketRecord> volumetric = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            volumetric.add(new PacketRecord(i, T0.plusMillis(i * 2L), "10.0.1.1", "192.168.1.5",
                    40000, 80, Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        List<ThreatCandidate> volFindings = run(rule, volumetric);
        assertThat(volFindings).hasSize(1);
        assertThat(volFindings.get(0).threatType()).isEqualTo(ThreatType.DDOS);

        // 4. Undecoded/unclassified link layer frames with destination '-' -> quiet
        List<PacketRecord> unclassified = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            unclassified.add(new PacketRecord(i, T0.plusMillis(i), "-", "-",
                    null, null, Protocol.OTHER, NetworkProtocol.OTHER, 60, 0, null, false));
        }
        assertThat(run(rule, unclassified)).isEmpty();
    }

    /* =========================================================================
     * 9. DnsAnomalyRule
     * ========================================================================= */
    @Test
    @DisplayName("DnsAnomalyRule: fires on query flooding and true tunneling (> 1232B), quiet on standard EDNS0 responses")
    void dnsAnomaly() {
        DnsAnomalyRule rule = new DnsAnomalyRule();

        // 1. High query volume: 500 queries at 25 qps -> fires
        List<PacketRecord> flood = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            flood.add(new PacketRecord(i, T0.plusMillis(i * 40L), "192.168.1.10", "8.8.8.8",
                    50000, 53, Protocol.DNS, NetworkProtocol.IPV4, 100, 60, null, false));
        }
        List<ThreatCandidate> floodFindings = run(rule, flood);
        assertThat(floodFindings).hasSize(1);
        assertThat(floodFindings.get(0).threatType()).isEqualTo(ThreatType.DNS_ANOMALY);

        // 2. Standard EDNS0 responses: 50 queries with 1000B payloads (<= 1232B standard buffer) -> quiet
        List<PacketRecord> edns0 = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            edns0.add(new PacketRecord(i, T0.plusSeconds(i), "192.168.1.10", "8.8.8.8",
                    50000, 53, Protocol.DNS, NetworkProtocol.IPV4, 1040, 1000, null, false));
        }
        assertThat(run(rule, edns0)).isEmpty();

        // 3. DNS tunneling: 50 queries where 25% exceed 1232B transport payload (e.g. 1400 bytes) -> fires
        List<PacketRecord> tunneling = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            int payload = (i <= 15) ? 1400 : 200;
            tunneling.add(new PacketRecord(i, T0.plusSeconds(i), "192.168.1.10", "8.8.8.8",
                    50000, 53, Protocol.DNS, NetworkProtocol.IPV4, payload + 40, payload, null, false));
        }
        List<ThreatCandidate> tunFindings = run(rule, tunneling);
        assertThat(tunFindings).hasSize(1);
        assertThat(tunFindings.get(0).threatType()).isEqualTo(ThreatType.DNS_ANOMALY);
        assertThat(tunFindings.get(0).title()).contains("Possible DNS tunnelling");

        // 4. DNS over TCP with standard payload sizes (e.g. 800 bytes) -> quiet
        List<PacketRecord> dnsOverTcp = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            dnsOverTcp.add(new PacketRecord(i, T0.plusSeconds(i), "192.168.1.10", "8.8.8.8",
                    50000, 53, Protocol.DNS, NetworkProtocol.IPV4, 840, 800, "ACK", false));
        }
        assertThat(run(rule, dnsOverTcp)).isEmpty();

        // 5. Corrupted/unclassified frames with null or '-' source IP -> quiet
        List<PacketRecord> unclassified = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            unclassified.add(new PacketRecord(i, T0.plusSeconds(i), "-", "8.8.8.8",
                    50000, 53, Protocol.DNS, NetworkProtocol.IPV4, 1500, 1400, null, false));
        }
        assertThat(run(rule, unclassified)).isEmpty();
    }

    /* =========================================================================
     * 10. RepeatedConnectionRule
     * ========================================================================= */
    @Test
    @DisplayName("RepeatedConnectionRule: fires on >= 100 SYN attempts to single endpoint, correctly recognizes HTTP SYNs")
    void repeatedConnection() {
        RepeatedConnectionRule rule = new RepeatedConnectionRule();

        // 1. Boundary below threshold: 99 attempts -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            below.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    40000 + (int) i, 8080, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN", false));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 100 attempts -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(new PacketRecord(100, T0.plusMillis(1000L), "10.0.0.1", "10.0.0.2",
                40100, 8080, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, "SYN", false));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.REPEATED_CONNECTION);

        // 3. Repeated connection storm to HTTP port 80 (protocol = Protocol.HTTP)
        List<PacketRecord> httpStorm = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            httpStorm.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    40000 + (int) i, 80, Protocol.HTTP, NetworkProtocol.IPV4, 60, 0, "SYN", false));
        }
        List<ThreatCandidate> httpFindings = run(rule, httpStorm);
        assertThat(httpFindings).hasSize(1);
        assertThat(httpFindings.get(0).threatType()).isEqualTo(ThreatType.REPEATED_CONNECTION);
    }

    /* =========================================================================
     * 11. SuspiciousExternalCommRule
     * ========================================================================= */
    @Test
    @DisplayName("SuspiciousExternalCommRule: fires on non-standard egress >= 20 pkts, ignores standard ports including DoT 853")
    void suspiciousExternalComm() {
        SuspiciousExternalCommRule rule = new SuspiciousExternalCommRule();

        // 1. Boundary below threshold: 19 packets to port 1337 -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int i = 1; i <= 19; i++) {
            below.add(new PacketRecord(i, T0.plusMillis(i * 10L), "192.168.1.10", "203.0.113.10",
                    50000, 1337, Protocol.TCP, NetworkProtocol.IPV4, 60, 20, null, false));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 20 packets to port 1337 -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(new PacketRecord(20, T0.plusMillis(200L), "192.168.1.10", "203.0.113.10",
                50000, 1337, Protocol.TCP, NetworkProtocol.IPV4, 60, 20, null, false));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.SUSPICIOUS_EXTERNAL_COMM);

        // 3. Standard egress ports: 50 packets to port 443 (HTTPS) and port 853 (DNS over TLS) -> quiet
        List<PacketRecord> standard = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            standard.add(new PacketRecord(i, T0.plusMillis(i * 10L), "192.168.1.10", "1.1.1.1",
                    50000, 853, Protocol.TCP, NetworkProtocol.IPV4, 60, 20, null, false));
        }
        assertThat(run(rule, standard)).isEmpty();
    }

    /* =========================================================================
     * 12. UdpScanRule
     * ========================================================================= */
    @Test
    @DisplayName("UdpScanRule: fires on >= 20 distinct UDP ports, ignores DNS (53) and DHCP (67, 68)")
    void udpScan() {
        UdpScanRule rule = new UdpScanRule();

        // 1. Boundary below threshold: 19 distinct UDP ports -> quiet
        List<PacketRecord> below = new ArrayList<>();
        for (int port = 2000; port < 2019; port++) {
            below.add(new PacketRecord(port, T0.plusMillis(port * 10L), "10.0.0.1", "10.0.0.2",
                    40000, port, Protocol.UDP, NetworkProtocol.IPV4, 60, 20, null, false));
        }
        assertThat(run(rule, below)).isEmpty();

        // 2. Boundary at threshold: 20 distinct UDP ports -> fires
        List<PacketRecord> at = new ArrayList<>(below);
        at.add(new PacketRecord(2020, T0.plusMillis(20200L), "10.0.0.1", "10.0.0.2",
                40000, 2020, Protocol.UDP, NetworkProtocol.IPV4, 60, 20, null, false));
        List<ThreatCandidate> findings = run(rule, at);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.UDP_SCAN);

        // 3. DNS (53) and DHCP (67, 68) datagrams are explicitly ignored -> quiet
        List<PacketRecord> infra = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            int port = (i % 2 == 0) ? 53 : 67;
            infra.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    40000, port, Protocol.UDP, NetworkProtocol.IPV4, 60, 20, null, false));
        }
        assertThat(run(rule, infra)).isEmpty();
    }

    /* =========================================================================
     * 13. UnknownProtocolRule
     * ========================================================================= */
    @Test
    @DisplayName("UnknownProtocolRule: fires on >= 100 packets with >= 30% unclassified, quiet on small or known captures")
    void unknownProtocol() {
        UnknownProtocolRule rule = new UnknownProtocolRule();

        // 1. Below minimum packets: 50 packets with 50% unknown -> quiet (< 100 packets)
        List<PacketRecord> small = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Protocol proto = (i % 2 == 0) ? Protocol.OTHER : Protocol.TCP;
            small.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    null, null, proto, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        assertThat(run(rule, small)).isEmpty();

        // 2. Boundary below ratio threshold: 100 packets with 29 unknown (29% < 30%) -> quiet
        List<PacketRecord> belowRatio = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Protocol proto = (i <= 29) ? Protocol.OTHER : Protocol.TCP;
            belowRatio.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    null, null, proto, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        assertThat(run(rule, belowRatio)).isEmpty();

        // 3. Boundary at ratio threshold: 100 packets with 30 unknown (30%) -> fires
        List<PacketRecord> atRatio = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Protocol proto = (i <= 30) ? Protocol.OTHER : Protocol.TCP;
            atRatio.add(new PacketRecord(i, T0.plusMillis(i * 10L), "10.0.0.1", "10.0.0.2",
                    null, null, proto, NetworkProtocol.IPV4, 60, 0, null, false));
        }
        List<ThreatCandidate> findings = run(rule, atRatio);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.UNKNOWN_PROTOCOL);
    }

    /* =========================================================================
     * Edge Cases: Empty Capture & Extreme Inputs
     * ========================================================================= */
    @Test
    @DisplayName("All 13 rules gracefully handle empty captures and null fields without throwing")
    void edgeCasesEmptyCapture() {
        List<DetectionRule> allRules = List.of(
                new AbnormalPacketSizeRule(),
                new BeaconingRule(),
                new BruteForceRule(),
                new DataExfiltrationRule(),
                new DdosRule(),
                new DnsAnomalyRule(),
                new IcmpFloodRule(),
                new PortScanRule(),
                new RepeatedConnectionRule(),
                new SuspiciousExternalCommRule(),
                new SynScanRule(),
                new UdpScanRule(),
                new UnknownProtocolRule());

        List<PacketRecord> emptyList = List.of();
        for (DetectionRule rule : allRules) {
            List<ThreatCandidate> findings = run(rule, emptyList);
            assertThat(findings).isEmpty();
        }

        // Corrupted records with null IPs or malformed flags
        List<PacketRecord> corrupted = List.of(
                new PacketRecord(1, null, null, null, null, null,
                        Protocol.OTHER, NetworkProtocol.OTHER, 0, 0, null, true),
                new PacketRecord(2, T0, "-", "-", null, null,
                        Protocol.OTHER, NetworkProtocol.OTHER, 0, 0, "", true));

        for (DetectionRule rule : allRules) {
            assertThat(run(rule, corrupted)).isNotNull();
        }

        // Test non-positive threshold constructor fallbacks across all 6 configurable rules
        assertThat(run(new BruteForceRule(0), emptyList)).isEmpty();
        assertThat(run(new BruteForceRule(-10), emptyList)).isEmpty();
        assertThat(run(new DataExfiltrationRule(0), emptyList)).isEmpty();
        assertThat(run(new DataExfiltrationRule(-100), emptyList)).isEmpty();
        assertThat(run(new DnsAnomalyRule(0), emptyList)).isEmpty();
        assertThat(run(new DnsAnomalyRule(-50), emptyList)).isEmpty();
        assertThat(run(new PortScanRule(0), emptyList)).isEmpty();
        assertThat(run(new PortScanRule(-1), emptyList)).isEmpty();
        assertThat(run(new SynScanRule(0), emptyList)).isEmpty();
        assertThat(run(new SynScanRule(-20), emptyList)).isEmpty();
        assertThat(run(new UdpScanRule(0), emptyList)).isEmpty();
        assertThat(run(new UdpScanRule(-5), emptyList)).isEmpty();
    }

    /* =========================================================================
     * Risk Scoring & Harmonization
     * ========================================================================= */
    @Test
    @DisplayName("RiskScorer: saturates cleanly, maps to bands, and prevents false SECURE on unscanned runs")
    void riskScoringAndHarmonization() {
        // Zero score
        assertThat(RiskScorer.score(0, 0, 0, 0)).isZero();
        assertThat(RiskScorer.status(0, 0, 0)).isEqualTo(OverallStatus.SECURE);

        // Saturation at 100
        assertThat(RiskScorer.score(10, 10, 10, 10)).isEqualTo(100);

        // Score 12 -> LOW_RISK
        assertThat(RiskScorer.score(0, 0, 2, 1)).isEqualTo(12);
        assertThat(RiskScorer.status(12, 0, 0)).isEqualTo(OverallStatus.LOW_RISK);

        // Boundary tests
        assertThat(RiskScorer.status(25, 0, 0)).isEqualTo(OverallStatus.ELEVATED);
        assertThat(RiskScorer.status(50, 0, 0)).isEqualTo(OverallStatus.HIGH_RISK);
        assertThat(RiskScorer.status(80, 0, 0)).isEqualTo(OverallStatus.CRITICAL);

        // Critical threat elevates to CRITICAL regardless of raw score
        assertThat(RiskScorer.status(10, 1, 0)).isEqualTo(OverallStatus.CRITICAL);
        // High threat elevates to HIGH_RISK
        assertThat(RiskScorer.status(10, 0, 1)).isEqualTo(OverallStatus.HIGH_RISK);

        // Status synchronization with detection complete state
        assertThat(RiskScorer.status(AnalysisStatus.QUEUED, false, 0, 0, 0)).isNull();
        assertThat(RiskScorer.status(AnalysisStatus.PROCESSING, false, 0, 0, 0)).isNull();
        assertThat(RiskScorer.status(AnalysisStatus.FAILED, false, 0, 0, 0)).isNull();
        assertThat(RiskScorer.status(AnalysisStatus.COMPLETED, false, 0, 0, 0)).isNull();
        assertThat(RiskScorer.status(AnalysisStatus.COMPLETED, true, 0, 0, 0)).isEqualTo(OverallStatus.SECURE);
    }
}
