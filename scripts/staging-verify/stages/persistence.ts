/**
 * Stage 6: Persistence Verification (Check 16).
 * Verifies object storage and database persistence across backend service restarts.
 *
 * Implements a two-phase protocol:
 *   --phase before: Uploads clean_traffic.pcap, starts inspection, waits for COMPLETED,
 *                   writes metadata to .persistence-state.json, instructs operator to restart Render.
 *   --phase after:  Reads .persistence-state.json, verifies file and analysis exist,
 *                   runs threat re-detection, asserts matching findings, cleans up.
 */

import fs from "node:fs";
import path from "node:path";
import type { CheckResult, PersistencePhase, PersistenceState, StagingConfig } from "../types.ts";
import type { StagingApiClient } from "../client.ts";

const CAPTURES_DIR = path.resolve("backend/src/test/resources/captures");
const STATE_FILE_PATH = path.resolve("scripts/staging-verify/.persistence-state.json");

export async function runPersistenceStage(
  client: StagingApiClient,
  _config: StagingConfig,
  phase?: PersistencePhase,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];

  // Determine which phase to execute
  let resolvedPhase: PersistencePhase;
  if (phase) {
    resolvedPhase = phase;
  } else if (fs.existsSync(STATE_FILE_PATH)) {
    // If state file already exists and no phase specified, execute "after"
    resolvedPhase = "after";
  } else {
    resolvedPhase = "before";
  }

  const start = Date.now();

  if (resolvedPhase === "before") {
    try {
      const filePath = path.join(CAPTURES_DIR, "clean_traffic.pcap");
      if (!fs.existsSync(filePath)) {
        throw new Error(`Fixture not found: ${filePath}`);
      }

      // Step 1: Upload clean_traffic.pcap
      const uploadRes = await client.uploadCapture(filePath, "persistence_clean_traffic.pcap");
      if (uploadRes.status !== 201) {
        throw Object.assign(new Error(`Upload failed with status ${uploadRes.status}`), {
          requestMethod: "POST",
          requestUrl: "/api/v1/files/upload",
          responseStatus: uploadRes.status,
          responseBodySnippet: client.redact(uploadRes.bodyText),
        });
      }

      const fileDto = (
        uploadRes.json as { data: { id: string; checksum: string; originalFileName: string } }
      ).data;
      const fileId = fileDto.id;

      // Step 2: Start inspection
      const startRes = await client.startInspection(fileId);
      if (startRes.status !== 202) {
        throw Object.assign(new Error(`Start inspection failed with status ${startRes.status}`), {
          requestMethod: "POST",
          requestUrl: `/api/v1/analysis/start/${fileId}`,
          responseStatus: startRes.status,
          responseBodySnippet: client.redact(startRes.bodyText),
        });
      }

      const analysisDto = (startRes.json as { data: { id: string } }).data;
      const analysisId = analysisDto.id;

      // Step 3: Poll until COMPLETED
      const terminal = await client.pollAnalysisUntilComplete(analysisId);
      if (terminal.analysis.status !== "COMPLETED") {
        throw new Error(`Analysis failed with status ${terminal.analysis.status}`);
      }

      // Step 4: Save state
      const state: PersistenceState = {
        fileId,
        analysisId,
        fileName: fileDto.originalFileName || "persistence_clean_traffic.pcap",
        checksum: fileDto.checksum,
        totalPackets: terminal.analysis.totalPackets,
        riskScore: terminal.analysis.riskScore,
        totalThreats: terminal.summary?.totalThreats ?? 0,
        timestamp: new Date().toISOString(),
      };

      await fs.promises.writeFile(STATE_FILE_PATH, JSON.stringify(state, null, 2), "utf-8");

      results.push({
        checkNumber: 16,
        name: "Storage & Analysis Persistence: Phase 1 (Pre-Restart Capture & Inspection)",
        stage: "persistence",
        status: "PASS",
        durationMs: Date.now() - start,
        sourceCitation: "StorageService + AnalysisService persistent storage validation",
        summary: `Pre-restart phase complete. File ${fileId} and analysis ${analysisId} saved to .persistence-state.json. Operator action required: restart backend on Render, then run --phase after.`,
        metadata: {
          fileId,
          analysisId,
          stateFilePath: STATE_FILE_PATH,
          totalPackets: state.totalPackets,
          nextStep: "bun run scripts/staging-verify/index.ts --stage persistence --phase after",
        },
      });
    } catch (err: unknown) {
      const error = err as Error & {
        requestMethod?: string;
        requestUrl?: string;
        responseStatus?: number;
        responseBodySnippet?: string;
      };
      results.push({
        checkNumber: 16,
        name: "Storage & Analysis Persistence: Phase 1 (Pre-Restart Capture & Inspection)",
        stage: "persistence",
        status: "FAIL",
        durationMs: Date.now() - start,
        sourceCitation: "StorageService + AnalysisService persistent storage validation",
        summary: `Pre-restart phase failed: ${error.message}`,
        failure: {
          requestMethod: error.requestMethod,
          requestUrl: error.requestUrl,
          responseStatus: error.responseStatus,
          responseBodySnippet: error.responseBodySnippet,
          errorMessage: error.message,
          reproductionSteps: [
            "Ensure staging backend is UP",
            "Upload clean_traffic.pcap via POST /api/v1/files/upload",
            "Trigger inspection via POST /api/v1/analysis/start/{fileId}",
            "Verify COMPLETED status and write .persistence-state.json",
          ],
        },
      });
    }
  } else {
    // Phase: after
    try {
      if (!fs.existsSync(STATE_FILE_PATH)) {
        throw new Error(
          `Persistence state file not found at ${STATE_FILE_PATH}. Run --phase before first.`,
        );
      }

      const stateRaw = await fs.promises.readFile(STATE_FILE_PATH, "utf-8");
      const state = JSON.parse(stateRaw) as PersistenceState;

      // Step 1: Verify file still exists and metadata matches
      const getFileRes = await client.getCapture(state.fileId);
      if (getFileRes.status !== 200) {
        throw Object.assign(
          new Error(`File ${state.fileId} not found after restart (HTTP ${getFileRes.status})`),
          {
            requestMethod: "GET",
            requestUrl: `/api/v1/files/${state.fileId}`,
            responseStatus: getFileRes.status,
            responseBodySnippet: client.redact(getFileRes.bodyText),
          },
        );
      }

      const fetchedFile = (getFileRes.json as { data: { checksum: string } }).data;
      if (fetchedFile.checksum !== state.checksum) {
        throw new Error(
          `File checksum mismatch after restart: expected ${state.checksum}, got ${fetchedFile.checksum}`,
        );
      }

      // Step 2: Verify analysis still exists
      const getAnalysisRes = await client.getAnalysis(state.analysisId);
      if (getAnalysisRes.status !== 200) {
        throw Object.assign(
          new Error(
            `Analysis ${state.analysisId} not found after restart (HTTP ${getAnalysisRes.status})`,
          ),
          {
            requestMethod: "GET",
            requestUrl: `/api/v1/analysis/${state.analysisId}`,
            responseStatus: getAnalysisRes.status,
            responseBodySnippet: client.redact(getAnalysisRes.bodyText),
          },
        );
      }

      // Step 3: Trigger re-detection via POST /api/v1/threats/run/{analysisId}
      const reRunRes = await client.reRunDetection(state.analysisId);
      if (reRunRes.status !== 200) {
        throw Object.assign(
          new Error(`Re-running detection after restart failed (HTTP ${reRunRes.status})`),
          {
            requestMethod: "POST",
            requestUrl: `/api/v1/threats/run/${state.analysisId}`,
            responseStatus: reRunRes.status,
            responseBodySnippet: client.redact(reRunRes.bodyText),
          },
        );
      }

      const detectionRun = (
        reRunRes.json as {
          data: {
            packetsScanned: number;
            totalThreats: number;
            riskScore: number;
          };
        }
      ).data;

      if (detectionRun.packetsScanned !== state.totalPackets) {
        throw new Error(
          `Packets scanned mismatch: expected ${state.totalPackets}, got ${detectionRun.packetsScanned}`,
        );
      }

      if (detectionRun.totalThreats !== state.totalThreats) {
        throw new Error(
          `Total threats mismatch: expected ${state.totalThreats}, got ${detectionRun.totalThreats}`,
        );
      }

      if (detectionRun.riskScore !== state.riskScore) {
        throw new Error(
          `Risk score mismatch: expected ${state.riskScore}, got ${detectionRun.riskScore}`,
        );
      }

      // Step 4: Clean up capture and state file
      await client.deleteCapture(state.fileId);
      await fs.promises.unlink(STATE_FILE_PATH).catch(() => {});

      results.push({
        checkNumber: 16,
        name: "Storage & Analysis Persistence: Phase 2 (Post-Restart Persistence & Detection Verification)",
        stage: "persistence",
        status: "PASS",
        durationMs: Date.now() - start,
        sourceCitation: "StorageService + AnalysisService persistent storage validation",
        summary: `Post-restart persistence confirmed. File ${state.fileId} and analysis ${state.analysisId} survived restart with identical checksum and detection outcomes. Cleaned up successfully.`,
        metadata: {
          fileId: state.fileId,
          analysisId: state.analysisId,
          packetsScanned: detectionRun.packetsScanned,
          totalThreats: detectionRun.totalThreats,
          riskScore: detectionRun.riskScore,
        },
      });
    } catch (err: unknown) {
      const error = err as Error & {
        requestMethod?: string;
        requestUrl?: string;
        responseStatus?: number;
        responseBodySnippet?: string;
      };
      results.push({
        checkNumber: 16,
        name: "Storage & Analysis Persistence: Phase 2 (Post-Restart Persistence & Detection Verification)",
        stage: "persistence",
        status: "FAIL",
        durationMs: Date.now() - start,
        sourceCitation: "StorageService + AnalysisService persistent storage validation",
        summary: `Post-restart persistence verification failed: ${error.message}`,
        failure: {
          requestMethod: error.requestMethod,
          requestUrl: error.requestUrl,
          responseStatus: error.responseStatus,
          responseBodySnippet: error.responseBodySnippet,
          errorMessage: error.message,
          reproductionSteps: [
            "Ensure --phase before was run and created .persistence-state.json",
            "Ensure Render backend restart finished and /actuator/health is UP",
            "Verify GET /api/v1/files/{fileId} and GET /api/v1/analysis/{analysisId}",
            "Verify POST /api/v1/threats/run/{analysisId} produces identical findings",
          ],
        },
      });
    }
  }

  return results;
}
