/**
 * Unit tests for storage reconciliation logic.
 */

import { describe, expect, it } from "bun:test";
import { reconcileStorageKeys } from "../reconcile-storage.ts";

describe("Storage Reconciliation Logic", () => {
  it("accurately matches DB stored file names against prefixed S3 object keys", () => {
    const dbFiles = ["capture-01.pcap", "traffic-02.pcapng"];
    const s3Keys = ["captures/capture-01.pcap", "captures/traffic-02.pcapng"];

    const result = reconcileStorageKeys(dbFiles, s3Keys, "captures/");
    expect(result.matchedCount).toBe(2);
    expect(result.missingInS3.length).toBe(0);
    expect(result.orphanedInS3.length).toBe(0);
  });

  it("identifies captures in DB that are missing from S3 storage", () => {
    const dbFiles = ["capture-01.pcap", "missing-02.pcap"];
    const s3Keys = ["captures/capture-01.pcap"];

    const result = reconcileStorageKeys(dbFiles, s3Keys, "captures/");
    expect(result.matchedCount).toBe(1);
    expect(result.missingInS3).toEqual(["captures/missing-02.pcap"]);
    expect(result.orphanedInS3.length).toBe(0);
  });

  it("identifies orphaned objects in S3 that have no DB reference", () => {
    const dbFiles = ["capture-01.pcap"];
    const s3Keys = ["captures/capture-01.pcap", "captures/orphaned-abandoned.pcap"];

    const result = reconcileStorageKeys(dbFiles, s3Keys, "captures/");
    expect(result.matchedCount).toBe(1);
    expect(result.missingInS3.length).toBe(0);
    expect(result.orphanedInS3).toEqual(["captures/orphaned-abandoned.pcap"]);
  });

  it("ignores S3 objects outside of designated prefix", () => {
    const dbFiles = ["capture-01.pcap"];
    const s3Keys = ["captures/capture-01.pcap", "other-folder/unrelated.txt"];

    const result = reconcileStorageKeys(dbFiles, s3Keys, "captures/");
    expect(result.matchedCount).toBe(1);
    expect(result.orphanedInS3.length).toBe(0); // Ignored because outside prefix
  });
});
