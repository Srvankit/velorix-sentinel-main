package com.velorix.sentinel.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.detection.rules.BeaconingRule;
import com.velorix.sentinel.detection.rules.BruteForceRule;
import com.velorix.sentinel.detection.rules.IcmpFloodRule;
import com.velorix.sentinel.detection.rules.PortScanRule;
import com.velorix.sentinel.detection.rules.SynScanRule;
import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.OverallStatus;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the detection rules.
 *
 * <p>Each test asserts both directions: the rule fires on the pattern it exists to catch,
 * and stays silent on benign traffic of the same protocol. A rule that only ever fires is
 * as useless as one that never does.</p>
 */
class DetectionRulesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static DetectionContext context() {
        return new DetectionContext(UUID.randomUUID(), 10_000, T0, T0.plusSeconds(600),
                DetectionLimits.defaults());
    }

    private static PacketRecord tcp(long number, long offsetMs, String src, String dst, int dstPort,
                                    String flags) {
        return new PacketRecord(number, T0.plusMillis(offsetMs), src, dst, 40000 + (int) (number % 1000),
                dstPort, Protocol.TCP, NetworkProtocol.IPV4, 60, 0, flags, false);
    }

    private static List<ThreatCandidate> run(DetectionRule rule, List<PacketRecord> packets) {
        RuleEvaluator evaluator = rule.newEvaluator(context());
        packets.forEach(evaluator::accept);
        return evaluator.finish();
    }

    @Test
    @DisplayName("port scan fires on many distinct ports and stays quiet on a busy single service")
    void portScan() {
        PortScanRule rule = new PortScanRule();

        List<PacketRecord> scan = new java.util.ArrayList<>();
        for (int port = 1; port <= 60; port++) {
            scan.add(tcp(port, port * 10L, "10.0.0.5", "10.0.0.9", port, "SYN"));
        }
        List<ThreatCandidate> findings = run(rule, scan);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.PORT_SCAN);
        assertThat(findings.get(0).sourceIp()).isEqualTo("10.0.0.5");
        assertThat(findings.get(0).evidence()).contains("distinctPorts=60");

        List<PacketRecord> browsing = new java.util.ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            browsing.add(tcp(i, i * 10L, "10.0.0.5", "10.0.0.9", 443, "SYN,ACK"));
        }
        assertThat(run(rule, browsing)).isEmpty();
    }

    @Test
    @DisplayName("SYN scan needs an abandoned handshake, not merely many SYNs")
    void synScan() {
        SynScanRule rule = new SynScanRule();

        List<PacketRecord> halfOpen = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            halfOpen.add(tcp(i, i * 5L, "10.0.0.5", "10.0.0.9", 1000 + i, "SYN"));
        }
        List<ThreatCandidate> findings = run(rule, halfOpen);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.SYN_SCAN);
        assertThat(findings.get(0).severity()).isIn(ThreatSeverity.HIGH, ThreatSeverity.CRITICAL);

        List<PacketRecord> completed = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            completed.add(tcp(i * 2L, i * 5L, "10.0.0.5", "10.0.0.9", 443, "SYN"));
            completed.add(tcp(i * 2L + 1, i * 5L + 1, "10.0.0.9", "10.0.0.5", 40000, "SYN,ACK"));
        }
        assertThat(run(rule, completed)).isEmpty();
    }

    @Test
    @DisplayName("ICMP flood is judged on rate, so a slow ping sweep is ignored")
    void icmpFlood() {
        IcmpFloodRule rule = new IcmpFloodRule();

        List<PacketRecord> flood = new java.util.ArrayList<>();
        for (int i = 1; i <= 2_000; i++) {
            flood.add(new PacketRecord(i, T0.plusMillis(i), "10.0.0.5", "10.0.0.9", null, null,
                    Protocol.ICMP, NetworkProtocol.IPV4, 98, 56, null, false));
        }
        assertThat(run(rule, flood)).singleElement()
                .extracting(ThreatCandidate::threatType).isEqualTo(ThreatType.ICMP_FLOOD);

        List<PacketRecord> heartbeat = new java.util.ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            heartbeat.add(new PacketRecord(i, T0.plusSeconds(i), "10.0.0.5", "10.0.0.9", null, null,
                    Protocol.ICMP, NetworkProtocol.IPV4, 98, 56, null, false));
        }
        assertThat(run(rule, heartbeat)).isEmpty();
    }

    @Test
    @DisplayName("brute force only fires against authentication ports")
    void bruteForce() {
        BruteForceRule rule = new BruteForceRule();

        List<PacketRecord> ssh = new java.util.ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            ssh.add(tcp(i, i * 100L, "203.0.113.7", "10.0.0.9", 22, "SYN"));
        }
        List<ThreatCandidate> findings = run(rule, ssh);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.BRUTE_FORCE);
        assertThat(findings.get(0).description()).contains("SSH");

        List<PacketRecord> web = new java.util.ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            web.add(tcp(i, i * 100L, "203.0.113.7", "10.0.0.9", 443, "SYN"));
        }
        assertThat(run(rule, web)).isEmpty();
    }

    @Test
    @DisplayName("beaconing keys off timing regularity, not volume")
    void beaconing() {
        BeaconingRule rule = new BeaconingRule();

        List<PacketRecord> beacon = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            beacon.add(tcp(i, i * 30_000L, "192.168.1.20", "198.51.100.4", 8443, "PSH,ACK"));
        }
        List<ThreatCandidate> findings = run(rule, beacon);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).threatType()).isEqualTo(ThreatType.BEACONING);
        assertThat(findings.get(0).evidence()).contains("meanIntervalSeconds=30.00");

        List<PacketRecord> human = new java.util.ArrayList<>();
        long offset = 0;
        long[] jitter = {1_000, 47_000, 3_000, 91_000, 12_000, 5_000, 130_000, 2_000, 60_000, 8_000,
                25_000, 4_000, 110_000, 7_000, 33_000};
        for (int i = 0; i < jitter.length; i++) {
            offset += jitter[i];
            human.add(tcp(i + 1, offset, "192.168.1.20", "198.51.100.4", 443, "PSH,ACK"));
        }
        assertThat(run(rule, human)).isEmpty();
    }

    @Test
    @DisplayName("risk score saturates and maps to a status")
    void riskScoring() {
        assertThat(RiskScorer.score(0, 0, 0, 0)).isZero();
        assertThat(RiskScorer.status(0, 0, 0)).isEqualTo(OverallStatus.SECURE);
        assertThat(RiskScorer.score(0, 0, 2, 1)).isEqualTo(12);
        assertThat(RiskScorer.score(10, 10, 10, 10)).isEqualTo(100);
        assertThat(RiskScorer.status(RiskScorer.score(1, 0, 0, 0), 1, 0))
                .isEqualTo(OverallStatus.CRITICAL);
    }
}
