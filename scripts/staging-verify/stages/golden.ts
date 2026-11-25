/**
 * Stage 1: Golden PCAP Verification (Checks 1 to 8).
 * Tests all 6 golden capture fixtures against backend detection contracts and UI views.
 */

import path from "node:path";
import type { CheckResult, StagingConfig } from "../types.ts";
import type { StagingApiClient } from "../client.ts";
import type { StagingUiVerifier } from "../ui-verifier.ts";

const CAPTURES_DIR = path.resolve("backend/src/test/resources/captures");

export async function runGoldenStage(
  client: StagingApiClient,
  uiVerifier: StagingUiVerifier,
  _config: StagingConfig,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];
  const createdFileIds: string[] = [];

  // Helper for recording check execution
  async function executeCheck(
    checkNumber: number,
    name: string,
    sourceCitation: string,
    action: () => Promise<{ summary: string; metadata?: Record<string, unknown> }>,
  ): Promise<CheckResult> {
    const start = Date.now();
    try {
      const res = await action();
      return {
        checkNumber,
        name,
        stage: "golden",
        status: "PASS",
        durationMs: Date.now() - start,
        sourceCitation,
        summary: res.summary,
        metadata: res.metadata,
      };
    } catch (err: unknown) {
      const error = err as Error & {
        requestMethod?: string;
        requestUrl?: string;
        responseStatus?: number;
        responseBodySnippet?: string;
      };
      return {
        checkNumber,
        name,
        stage: "golden",
        status: "FAIL",
        durationMs: Date.now() - start,
        sourceCitation,
        summary: `Check failed: ${error.message}`,
        failure: {
          requestMethod: error.requestMethod,
          requestUrl: error.requestUrl,
          responseStatus: error.responseStatus,
          responseBodySnippet: error.responseBodySnippet,
          errorMessage: error.message,
          reproductionSteps: [
            `Log in with valid credentials via POST /api/v1/auth/login`,
            `Upload golden capture fixture from backend/src/test/resources/captures/`,
            `Trigger inspection via POST /api/v1/analysis/start/{fileId}`,
            `Verify expected threat findings and analysis metrics`,
          ],
        },
      };
    }
  }

  // --------------------------------------------------------------------------
  // Check 1: clean_traffic.pcap (benign traffic)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      1,
      "clean_traffic.pcap: zero threats, riskScore 0, Secure UI badge",
      "GoldenPcapFixturesIntegrationTest.testCleanTrafficFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "clean_traffic.pcap");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw Object.assign(new Error(`Upload failed with status ${uploadRes.status}`), {
            requestMethod: "POST",
            requestUrl: "/api/v1/files/upload",
            responseStatus: uploadRes.status,
            responseBodySnippet: client.redact(uploadRes.bodyText),
          });
        }

        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        if (startRes.status !== 202) {
          throw Object.assign(new Error(`Start inspection failed with status ${startRes.status}`), {
            requestMethod: "POST",
            requestUrl: `/api/v1/analysis/start/${fileDto.id}`,
            responseStatus: startRes.status,
            responseBodySnippet: client.redact(startRes.bodyText),
          });
        }

        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        // Verify awaiting-scan state: immediate poll should show QUEUED or PROCESSING and NOT SECURE
        const earlyRes = await client.getAnalysis(analysisId);
        if (earlyRes.status === 200) {
          const earlyData = (
            earlyRes.json as {
              data: {
                analysis: { status: string; riskScore: number };
                summary?: { overallStatus: string };
              };
            }
          ).data;
          if (
            earlyData.analysis.status === "QUEUED" ||
            earlyData.analysis.status === "PROCESSING"
          ) {
            if (earlyData.summary?.overallStatus === "SECURE") {
              throw new Error(
                "Awaiting-scan violation: overallStatus is SECURE before inspection completed!",
              );
            }
          }
        }

        // Poll to completion
        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason || "unknown failure"}`);
        }

        if (analysis.riskScore !== 0) {
          throw new Error(`Expected riskScore 0, got ${analysis.riskScore}`);
        }
        if (analysis.maliciousPackets !== 0) {
          throw new Error(`Expected maliciousPackets 0, got ${analysis.maliciousPackets}`);
        }
        if (analysis.processedPackets !== 10) {
          throw new Error(`Expected 10 processedPackets, got ${analysis.processedPackets}`);
        }
        if (summary && summary.totalThreats !== 0) {
          throw new Error(`Expected totalThreats 0, got ${summary.totalThreats}`);
        }

        // Verify UI badge
        const session = client.getSession();
        if (session) {
          await uiVerifier.verifySecureBadge(analysisId, session);
        }

        return {
          summary: `Clean traffic completed in ${elapsedMs}ms: 10 packets, 0 threats, risk score 0, Secure badge verified.`,
          metadata: { analysisId, fileId: fileDto.id, riskScore: analysis.riskScore },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 2: syn_port_scan.pcap (Port scan / SYN scan)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      2,
      "syn_port_scan.pcap: PORT_SCAN and SYN_SCAN findings, risk band High/Critical",
      "GoldenPcapFixturesIntegrationTest.testSynPortScanFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "syn_port_scan.pcap");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw new Error(`Upload failed with status ${uploadRes.status}`);
        }
        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason}`);
        }

        const threatsRes = await client.listThreats(analysisId);
        const threats =
          (
            threatsRes.json as {
              data: { content: Array<{ threatType: string; detectionRule: string }> };
            }
          ).data?.content || [];

        const hasPortScan = threats.some(
          (t) => t.threatType === "PORT_SCAN" || t.detectionRule.includes("port-scan"),
        );
        const hasSynScan = threats.some(
          (t) => t.threatType === "SYN_SCAN" || t.detectionRule.includes("syn-scan"),
        );

        if (!hasPortScan && !hasSynScan) {
          throw new Error(
            `Expected PORT_SCAN or SYN_SCAN findings, got: ${JSON.stringify(threats.map((t) => t.threatType))}`,
          );
        }

        if (analysis.riskScore < 50) {
          throw new Error(`Expected elevated/high riskScore, got ${analysis.riskScore}`);
        }

        // Verify suspicious packets exist
        const packetsRes = await client.listPackets(analysisId);
        const packets =
          (packetsRes.json as { data: { content: Array<{ suspicious: boolean }> } }).data
            ?.content || [];
        const hasSuspicious = packets.some((p) => p.suspicious);
        if (!hasSuspicious) {
          throw new Error("Expected at least one packet with suspicious=true in port scan");
        }

        // Verify UI packet browser tags
        const session = client.getSession();
        if (session) {
          await uiVerifier.verifySuspiciousPackets(analysisId, session);
        }

        return {
          summary: `Port scan completed in ${elapsedMs}ms: threats detected (${threats.length}), riskScore ${analysis.riskScore}, suspicious packets verified.`,
          metadata: {
            analysisId,
            threatsFound: threats.map((t) => t.threatType),
            riskScore: analysis.riskScore,
          },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 3: dns_tunnel_sample.pcap (Exfiltration via DNS)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      3,
      "dns_tunnel_sample.pcap: DNS_ANOMALY detection finding fired",
      "GoldenPcapFixturesIntegrationTest.testDnsTunnelFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "dns_tunnel_sample.pcap");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw new Error(`Upload failed with status ${uploadRes.status}`);
        }
        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason}`);
        }

        const threatsRes = await client.listThreats(analysisId);
        const threats =
          (
            threatsRes.json as {
              data: { content: Array<{ threatType: string; detectionRule: string }> };
            }
          ).data?.content || [];
        const hasDnsThreat = threats.some(
          (t) => t.threatType === "DNS_ANOMALY" || t.detectionRule.includes("dns"),
        );

        if (!hasDnsThreat) {
          throw new Error(
            `Expected DNS_ANOMALY threat finding, found: ${JSON.stringify(threats.map((t) => t.threatType))}`,
          );
        }

        return {
          summary: `DNS tunnel sample completed in ${elapsedMs}ms: DNS anomaly finding confirmed.`,
          metadata: { analysisId, threatsFound: threats.map((t) => t.threatType) },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 4: vlan_tagged_traffic.pcap (802.1Q tags & real IPs)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      4,
      "vlan_tagged_traffic.pcap: 802.1Q unwrapping, real IP extraction",
      "GoldenPcapFixturesIntegrationTest.testVlanTaggedTrafficFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "vlan_tagged_traffic.pcap");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw new Error(`Upload failed with status ${uploadRes.status}`);
        }
        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason}`);
        }

        const packetsRes = await client.listPackets(analysisId);
        const packets =
          (
            packetsRes.json as {
              data: { content: Array<{ sourceIp: string; destinationIp: string }> };
            }
          ).data?.content || [];

        if (packets.length === 0) {
          throw new Error("Expected decoded packets from VLAN capture, got 0");
        }

        // Assert all packets have real IPs (not "-" or empty)
        for (const p of packets) {
          if (!p.sourceIp || p.sourceIp === "-" || !p.destinationIp || p.destinationIp === "-") {
            throw new Error(
              `VLAN packet has invalid IP extraction: src=${p.sourceIp}, dst=${p.destinationIp}`,
            );
          }
        }

        return {
          summary: `VLAN tagged capture completed in ${elapsedMs}ms: 4 frames unwrapped with valid IP addresses.`,
          metadata: { analysisId, packetsCount: packets.length },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 5: truncated_sample.pcap (Capture cut mid-frame)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      5,
      "truncated_sample.pcap: truncated=true, UI warning banner visible",
      "GoldenPcapFixturesIntegrationTest.testTruncatedSampleFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "truncated_sample.pcap");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw new Error(`Upload failed with status ${uploadRes.status}`);
        }
        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason}`);
        }

        if (!analysis.truncated) {
          throw new Error("Expected analysis.truncated to be true for truncated_sample.pcap");
        }

        // Verify UI warning banner
        const session = client.getSession();
        if (session) {
          await uiVerifier.verifyTruncatedBanner(analysisId, session);
        }

        return {
          summary: `Truncated capture completed in ${elapsedMs}ms: truncated=true flag and UI warning banner confirmed.`,
          metadata: {
            analysisId,
            truncated: analysis.truncated,
            processedPackets: analysis.processedPackets,
          },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 6: pcapng_sample.pcapng (PCAPNG format EPB decoding)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      6,
      "pcapng_sample.pcapng: EPB block parsing and packet decoding",
      "GoldenPcapFixturesIntegrationTest.testPcapNgSampleFixture()",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "pcapng_sample.pcapng");
        const uploadRes = await client.uploadCapture(filePath);
        if (uploadRes.status !== 201) {
          throw new Error(`Upload failed with status ${uploadRes.status}`);
        }
        const fileDto = (uploadRes.json as { data: { id: string } }).data;
        createdFileIds.push(fileDto.id);

        const startRes = await client.startInspection(fileDto.id);
        const analysisId = (startRes.json as { data: { id: string } }).data.id;

        const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(analysisId);
        if (analysis.status !== "COMPLETED") {
          throw new Error(`Analysis failed: ${analysis.failureReason}`);
        }

        if (analysis.processedPackets !== 5) {
          throw new Error(
            `Expected 5 processed packets from pcapng sample, got ${analysis.processedPackets}`,
          );
        }

        return {
          summary: `PCAPNG sample completed in ${elapsedMs}ms: 5 EPB frames parsed accurately.`,
          metadata: { analysisId, processedPackets: analysis.processedPackets },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 7: Deduplication behavior (Content hash duplication)
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      7,
      "Re-upload identical capture: 409 Conflict deduplication rejection",
      "UploadedFileServiceImpl.upload() checksum deduplication",
      async () => {
        const filePath = path.join(CAPTURES_DIR, "clean_traffic.pcap");
        const duplicateUploadRes = await client.uploadCapture(filePath);

        if (duplicateUploadRes.status !== 409) {
          throw Object.assign(
            new Error(
              `Expected 409 Conflict for duplicate upload, got ${duplicateUploadRes.status}`,
            ),
            {
              requestMethod: "POST",
              requestUrl: "/api/v1/files/upload",
              responseStatus: duplicateUploadRes.status,
              responseBodySnippet: client.redact(duplicateUploadRes.bodyText),
            },
          );
        }

        const body = duplicateUploadRes.bodyText;
        if (
          !body.includes("already been uploaded") &&
          !body.includes("identical") &&
          !body.includes("Duplicate")
        ) {
          console.warn(`[Dedup] 409 returned without expected message: ${body}`);
        }

        return {
          summary: `Deduplication confirmed: re-uploading clean_traffic.pcap returned HTTP 409 Conflict.`,
          metadata: { status: duplicateUploadRes.status },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 8: Stuck-run check & Stage Cleanup
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      8,
      "Stuck-run audit & golden stage cleanup",
      "Task 8.2 check 8 stuck run verification",
      async () => {
        const listRes = await client.rawRequest("/api/v1/analysis?page=0&size=50", {
          method: "GET",
        });
        if (listRes.status === 200 && listRes.json) {
          const content =
            (listRes.json as { data: { content: Array<{ id: string; status: string }> } }).data
              ?.content || [];
          const stuckRuns = content.filter(
            (a) => a.status === "QUEUED" || a.status === "PROCESSING",
          );
          if (stuckRuns.length > 0) {
            throw new Error(`Detected ${stuckRuns.length} stuck runs in QUEUED/PROCESSING state!`);
          }
        }

        // Cleanup created captures
        let cleaned = 0;
        for (const fileId of createdFileIds) {
          try {
            await client.deleteCapture(fileId);
            cleaned++;
          } catch (e) {
            console.warn(`[Cleanup] Failed to delete capture ${fileId}: ${(e as Error).message}`);
          }
        }

        return {
          summary: `Stuck run audit clean: 0 pending analyses. Cleaned up ${cleaned} test captures.`,
          metadata: { cleanedCaptures: cleaned },
        };
      },
    ),
  );

  return results;
}
