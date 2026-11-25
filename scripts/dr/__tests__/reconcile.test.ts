/**
 * Unit tests for storage reconciliation logic.
 * Task 8.3: Storage Reconciler Unit Tests covering key-building, prefix handling, and soft-delete exclusion.
 */

import { describe, expect, it } from "bun:test";
import { reconcileStorageKeys, resolveS3Key, type DbCaptureRecord } from "../reconcile-storage.ts";

describe("Storage Key Building Logic (S3CompatibleFileStorageService Alignment)", () => {
  it("prepends default prefix to clean storage key", () => {
    const s3Key = resolveS3Key("session-01.pcap", "captures/");
    expect(s3Key).toBe("captures/session-01.pcap");
  });

  it("strips leading forward slashes and normalizes backslashes", () => {
    const s3Key = resolveS3Key("///nested\\capture-02.pcapng", "captures/");
    expect(s3Key).toBe("captures/nested/capture-02.pcapng");
  });

  it("supports empty prefix or custom prefix without trailing slash", () => {
    const s3KeyEmpty = resolveS3Key("plain.pcap", "");
    expect(s3KeyEmpty).toBe("plain.pcap");

    const s3KeyCustom = resolveS3Key("plain.pcap", "staging-captures/");
    expect(s3KeyCustom).toBe("staging-captures/plain.pcap");
  });

  it("rejects path traversal attempts with directory traversal error", () => {
    expect(() => resolveS3Key("../escape.pcap")).toThrow("directory traversal rejected");
    expect(() => resolveS3Key("folder/../../escape.pcap")).toThrow("directory traversal rejected");
  });
});

describe("Storage Reconciliation & Soft-Delete Exclusion", () => {
  it("accurately matches active DB records against prefixed S3 object keys", () => {
    const dbRecords: DbCaptureRecord[] = [
      { storedFileName: "capture-01.pcap", uploadStatus: "UPLOADED" },
      { storedFileName: "capture-02.pcapng", uploadStatus: "READY_FOR_ANALYSIS" },
    ];
    const s3Keys = ["captures/capture-01.pcap", "captures/capture-02.pcapng"];

    const result = reconcileStorageKeys(dbRecords, s3Keys, "captures/");
    expect(result.activeDbCount).toBe(2);
    expect(result.softDeletedCount).toBe(0);
    expect(result.matchedCount).toBe(2);
    expect(result.missingInS3.length).toBe(0);
    expect(result.orphanedInS3.length).toBe(0);
  });

  it("EXCLUDES soft-deleted records (upload_status = 'DELETED') from expected S3 objects", () => {
    const dbRecords: DbCaptureRecord[] = [
      { storedFileName: "active-01.pcap", uploadStatus: "UPLOADED" },
      // Soft-deleted by user: binary removed from storage, audit row remains in DB
      { storedFileName: "deleted-by-user.pcap", uploadStatus: "DELETED" },
    ];
    // S3 only has the active file (which is correct!)
    const s3Keys = ["captures/active-01.pcap"];

    const result = reconcileStorageKeys(dbRecords, s3Keys, "captures/");
    expect(result.activeDbCount).toBe(1);
    expect(result.softDeletedCount).toBe(1);
    expect(result.matchedCount).toBe(1);
    // CRITICAL: deleted-by-user.pcap must NOT be reported as missing in S3!
    expect(result.missingInS3.length).toBe(0);
    expect(result.orphanedInS3.length).toBe(0);
  });

  it("identifies active DB captures that are missing from S3 storage", () => {
    const dbRecords: DbCaptureRecord[] = [
      { storedFileName: "active-01.pcap", uploadStatus: "UPLOADED" },
      { storedFileName: "missing-from-bucket.pcap", uploadStatus: "UPLOADED" },
    ];
    const s3Keys = ["captures/active-01.pcap"];

    const result = reconcileStorageKeys(dbRecords, s3Keys, "captures/");
    expect(result.activeDbCount).toBe(2);
    expect(result.matchedCount).toBe(1);
    expect(result.missingInS3).toEqual(["captures/missing-from-bucket.pcap"]);
    expect(result.orphanedInS3.length).toBe(0);
  });

  it("identifies orphaned objects in S3 that have no active DB record", () => {
    const dbRecords: DbCaptureRecord[] = [
      { storedFileName: "active-01.pcap", uploadStatus: "UPLOADED" },
      { storedFileName: "deleted-by-user.pcap", uploadStatus: "DELETED" },
    ];
    // S3 has active file AND an orphaned file (e.g. from an aborted upload)
    const s3Keys = ["captures/active-01.pcap", "captures/orphaned-abandoned.pcap"];

    const result = reconcileStorageKeys(dbRecords, s3Keys, "captures/");
    expect(result.matchedCount).toBe(1);
    expect(result.missingInS3.length).toBe(0);
    expect(result.orphanedInS3).toEqual(["captures/orphaned-abandoned.pcap"]);
  });

  it("handles custom prefix and backward-compatible string array inputs", () => {
    const dbFiles = ["sub/custom-prefix.pcap"];
    const s3Keys = ["archive/sub/custom-prefix.pcap"];

    const result = reconcileStorageKeys(dbFiles, s3Keys, "archive/");
    expect(result.matchedCount).toBe(1);
    expect(result.missingInS3.length).toBe(0);
  });
});
