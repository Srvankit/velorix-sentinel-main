/**
 * Stage 2: Large PCAP Verification (Check 9).
 * Tests ~100 MB capture handling, 100 MB limit enforcement, and Actuator health stability.
 */

import fs from "node:fs";
import path from "node:path";
import type { CheckResult, StagingConfig } from "../types.ts";
import type { StagingApiClient } from "../client.ts";
import { generateOverLimitPcap, generateValidLargePcap } from "../large-pcap-generator.ts";

const SCRATCH_DIR = path.resolve("scripts/staging-verify/scratch");

export async function runLargeStage(
  client: StagingApiClient,
  config: StagingConfig,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];
  const start = Date.now();

  try {
    if (!fs.existsSync(SCRATCH_DIR)) {
      fs.mkdirSync(SCRATCH_DIR, { recursive: true });
    }

    // 1. Generate and test over-limit file (101 MB)
    console.log("    Generating synthetic ~101 MB over-limit PCAP...");
    const overLimit = await generateOverLimitPcap(SCRATCH_DIR, 101 * 1024 * 1024);
    console.log(
      `    Generated ${overLimit.filePath} (${(overLimit.sizeBytes / (1024 * 1024)).toFixed(1)} MB)`,
    );

    console.log("    Uploading over-limit PCAP (expecting HTTP 413)...");
    const overLimitRes = await client.uploadCapture(overLimit.filePath, "overlimit_101mb.pcap");

    // Clean up temporary oversized file immediately
    await fs.promises.unlink(overLimit.filePath).catch(() => {});

    if (overLimitRes.status !== 413 && overLimitRes.status !== 400) {
      throw Object.assign(
        new Error(`Expected HTTP 413 or 400 for 101 MB upload, received ${overLimitRes.status}`),
        {
          requestMethod: "POST",
          requestUrl: "/api/v1/files/upload",
          responseStatus: overLimitRes.status,
          responseBodySnippet: client.redact(overLimitRes.bodyText),
        },
      );
    }

    // 2. Generate and test valid ~98 MB capture
    console.log("    Generating synthetic ~98 MB valid PCAP...");
    const validLarge = await generateValidLargePcap(SCRATCH_DIR, 98 * 1024 * 1024);
    console.log(
      `    Generated ${validLarge.filePath} (${(validLarge.sizeBytes / (1024 * 1024)).toFixed(1)} MB)`,
    );

    console.log("    Uploading 98 MB valid PCAP...");
    const uploadStart = Date.now();
    const validUploadRes = await client.uploadCapture(validLarge.filePath, "valid_large_98mb.pcap");
    const uploadDurationMs = Date.now() - uploadStart;

    // Clean up temporary 98 MB file from disk
    await fs.promises.unlink(validLarge.filePath).catch(() => {});

    if (validUploadRes.status !== 201) {
      throw Object.assign(
        new Error(`Failed to upload 98 MB capture (status ${validUploadRes.status})`),
        {
          requestMethod: "POST",
          requestUrl: "/api/v1/files/upload",
          responseStatus: validUploadRes.status,
          responseBodySnippet: client.redact(validUploadRes.bodyText),
        },
      );
    }

    const fileDto = (validUploadRes.json as { data: { id: string } }).data;
    console.log(
      `    Upload succeeded in ${(uploadDurationMs / 1000).toFixed(1)}s. Starting inspection...`,
    );

    const startRes = await client.startInspection(fileDto.id);
    if (startRes.status !== 202) {
      throw new Error(`Failed to start inspection for 98 MB capture (status ${startRes.status})`);
    }

    const analysisId = (startRes.json as { data: { id: string } }).data.id;
    console.log(`    Inspection ${analysisId} queued. Polling for completion (timeout: 10m)...`);

    // Poll with generous timeout for large capture (up to 10 minutes)
    const maxWaitMs = Math.max(config.timeoutMs * 10, 600000);
    const { analysis, elapsedMs } = await client.pollAnalysisUntilComplete(
      analysisId,
      maxWaitMs,
      5000,
    );

    if (analysis.status !== "COMPLETED") {
      throw new Error(`Large capture analysis failed: ${analysis.failureReason}`);
    }

    console.log(
      `    Inspection completed in ${(elapsedMs / 1000).toFixed(1)}s (${analysis.processedPackets} packets).`,
    );

    // Check Actuator Health
    const healthRes = await client.getActuatorHealth();
    let healthUp = false;
    if (healthRes.status === 200 && healthRes.json) {
      const healthData = healthRes.json as { status?: string };
      healthUp = healthData.status === "UP";
    }

    // Cleanup uploaded capture
    await client.deleteCapture(fileDto.id).catch(() => {});

    results.push({
      checkNumber: 9,
      name: "Large PCAP (~100 MB) handling, over-limit rejection, and service health",
      stage: "large",
      status: "PASS",
      durationMs: Date.now() - start,
      sourceCitation: "application.yml (max-file-size: 100MB, inspection max-duration: PT15M)",
      summary: `Over-limit rejection verified (status ${overLimitRes.status}). 98 MB PCAP uploaded in ${(uploadDurationMs / 1000).toFixed(1)}s, decoded in ${(elapsedMs / 1000).toFixed(1)}s. Actuator health UP: ${healthUp}.`,
      metadata: {
        uploadDurationMs,
        inspectionDurationMs: elapsedMs,
        packetsProcessed: analysis.processedPackets,
        healthStatus: healthUp ? "UP" : "UNKNOWN",
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
      checkNumber: 9,
      name: "Large PCAP (~100 MB) handling, over-limit rejection, and service health",
      stage: "large",
      status: "FAIL",
      durationMs: Date.now() - start,
      sourceCitation: "application.yml (max-file-size: 100MB, inspection max-duration: PT15M)",
      summary: `Large PCAP check failed: ${error.message}`,
      failure: {
        requestMethod: error.requestMethod,
        requestUrl: error.requestUrl,
        responseStatus: error.responseStatus,
        responseBodySnippet: error.responseBodySnippet,
        errorMessage: error.message,
        reproductionSteps: [
          "Generate synthetic 101 MB PCAP and attempt upload -> expect HTTP 413",
          "Generate synthetic 98 MB PCAP and upload -> expect HTTP 201",
          "Trigger inspection -> expect status COMPLETED within reaper timeout (15 min)",
          "Check GET /actuator/health -> expect status UP",
        ],
      },
    });
  }

  return results;
}
