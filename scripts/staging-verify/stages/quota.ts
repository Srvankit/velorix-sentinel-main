/**
 * Stage 3: Storage & File Quota Verification (Check 10).
 * Verifies per-user capture count limit (10 files), 409 Conflict quota rejection,
 * capture deletion, and subsequent quota relief.
 */

import fs from "node:fs";
import path from "node:path";
import type { CheckResult, StagingConfig } from "../types.ts";
import { StagingApiClient } from "../client.ts";
import { generatePcapFile } from "../large-pcap-generator.ts";

const SCRATCH_DIR = path.resolve("scripts/staging-verify/scratch/quota");

export async function runQuotaStage(
  client: StagingApiClient,
  config: StagingConfig,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];
  const start = Date.now();

  try {
    if (!fs.existsSync(SCRATCH_DIR)) {
      fs.mkdirSync(SCRATCH_DIR, { recursive: true });
    }

    // Determine which account to use: prefer Account 2 to isolate quota uploads from Account 1 rate limits
    let quotaClient = client;
    if (config.testEmail2 && config.testPassword2) {
      console.log(
        "    Using secondary test account (STAGING_TEST_EMAIL_2) for quota test to protect upload rate limits...",
      );
      quotaClient = new StagingApiClient(config.apiUrl);
      const loginRes = await quotaClient.login(config.testEmail2, config.testPassword2);
      if (loginRes.status !== 200) {
        throw new Error(`Failed to log in with STAGING_TEST_EMAIL_2: status ${loginRes.status}`);
      }
    } else {
      console.warn(
        "    STAGING_TEST_EMAIL_2 not provided. Using primary account for quota test (consumes upload rate budget).",
      );
    }

    // Clean up any existing captures on the quota account first
    const existingListRes = await quotaClient.listCaptures();
    if (existingListRes.status === 200 && existingListRes.json) {
      const items =
        (existingListRes.json as { data: { content: Array<{ id: string }> } }).data?.content || [];
      for (const item of items) {
        await quotaClient.deleteCapture(item.id).catch(() => {});
      }
    }

    const uploadedIds: string[] = [];

    // Generate and upload 10 distinct files (each with different content to prevent dedup rejection)
    console.log("    Uploading 10 unique small captures to fill quota...");
    for (let i = 1; i <= 10; i++) {
      const pcapPath = path.join(SCRATCH_DIR, `quota_test_${i}.pcap`);
      await generatePcapFile(pcapPath, 1024 * (10 + i), 500); // Unique size and bytes

      const uploadRes = await quotaClient.uploadCapture(pcapPath, `quota_test_${i}.pcap`);
      await fs.promises.unlink(pcapPath).catch(() => {});

      if (uploadRes.status !== 201) {
        throw Object.assign(new Error(`Failed on upload ${i}/10 (status ${uploadRes.status})`), {
          requestMethod: "POST",
          requestUrl: "/api/v1/files/upload",
          responseStatus: uploadRes.status,
          responseBodySnippet: quotaClient.redact(uploadRes.bodyText),
        });
      }

      const fileDto = (uploadRes.json as { data: { id: string } }).data;
      uploadedIds.push(fileDto.id);
    }

    // 11th upload must trip the 10-file quota
    console.log("    Attempting 11th upload (expecting 409 Conflict quota rejection)...");
    const pcapPath11 = path.join(SCRATCH_DIR, "quota_test_11.pcap");
    await generatePcapFile(pcapPath11, 1024 * 30, 500);

    const tripRes = await quotaClient.uploadCapture(pcapPath11, "quota_test_11.pcap");
    await fs.promises.unlink(pcapPath11).catch(() => {});

    // Note: Plan mentions 409 Conflict, earlier audit mentions 400. In code, ResourceConflictException returns 409.
    if (tripRes.status !== 409) {
      throw Object.assign(
        new Error(`Expected HTTP 409 Conflict for 11th upload, received ${tripRes.status}`),
        {
          requestMethod: "POST",
          requestUrl: "/api/v1/files/upload",
          responseStatus: tripRes.status,
          responseBodySnippet: quotaClient.redact(tripRes.bodyText),
        },
      );
    }

    if (
      !tripRes.bodyText.includes("quota exceeded") &&
      !tripRes.bodyText.includes("max 10 files")
    ) {
      console.warn(
        `[Quota] 409 response did not contain expected message text: ${tripRes.bodyText}`,
      );
    }

    // Delete 1 file to relieve quota
    const deleteTargetId = uploadedIds.pop()!;
    console.log(`    Deleting capture ${deleteTargetId} to relieve quota...`);
    const delRes = await quotaClient.deleteCapture(deleteTargetId);
    if (delRes.status !== 200) {
      throw new Error(`Failed to delete capture: status ${delRes.status}`);
    }

    // Verify deleted file is no longer accessible
    const fetchDeletedRes = await quotaClient.getCapture(deleteTargetId);
    if (fetchDeletedRes.status !== 404) {
      throw new Error(
        `Expected HTTP 404 fetching deleted capture, received ${fetchDeletedRes.status}`,
      );
    }

    // Attempt upload again — must succeed now that file count is 9
    console.log("    Uploading new file after deletion (expecting HTTP 201)...");
    const pcapPathRetry = path.join(SCRATCH_DIR, "quota_test_relief.pcap");
    await generatePcapFile(pcapPathRetry, 1024 * 35, 500);

    const reliefRes = await quotaClient.uploadCapture(pcapPathRetry, "quota_test_relief.pcap");
    await fs.promises.unlink(pcapPathRetry).catch(() => {});

    if (reliefRes.status !== 201) {
      throw new Error(`Expected HTTP 201 after deleting file, received ${reliefRes.status}`);
    }

    const reliefId = (reliefRes.json as { data: { id: string } }).data.id;
    uploadedIds.push(reliefId);

    // Clean up all remaining quota captures
    for (const id of uploadedIds) {
      await quotaClient.deleteCapture(id).catch(() => {});
    }

    results.push({
      checkNumber: 10,
      name: "Capture file quota (max 10 files) rejection, deletion, and upload restoration",
      stage: "quota",
      status: "PASS",
      durationMs: Date.now() - start,
      sourceCitation: "UploadedFileServiceImpl.upload() lines 104-113 (maxFilesPerUser: 10)",
      summary:
        "10 files uploaded, 11th rejected with HTTP 409 Conflict. Deletion confirmed (404 on get). Re-upload succeeded with HTTP 201.",
      metadata: {
        discrepancyNote:
          "Plan specifies 409 Conflict; code throws ResourceConflictException resulting in HTTP 409 (aligned).",
        quotaTrippedStatus: tripRes.status,
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
      checkNumber: 10,
      name: "Capture file quota (max 10 files) rejection, deletion, and upload restoration",
      stage: "quota",
      status: "FAIL",
      durationMs: Date.now() - start,
      sourceCitation: "UploadedFileServiceImpl.upload() lines 104-113 (maxFilesPerUser: 10)",
      summary: `Quota test failed: ${error.message}`,
      failure: {
        requestMethod: error.requestMethod,
        requestUrl: error.requestUrl,
        responseStatus: error.responseStatus,
        responseBodySnippet: error.responseBodySnippet,
        errorMessage: error.message,
        reproductionSteps: [
          "Upload 10 unique capture files to fill account quota",
          "Attempt 11th upload -> assert HTTP 409 Conflict",
          "Delete 1 file via DELETE /api/v1/files/{id}",
          "Upload 1 new file -> assert HTTP 201 Created",
          "Fetch deleted file -> assert HTTP 404 Not Found",
        ],
      },
    });
  }

  return results;
}
