/**
 * Unit tests for staging verification reporter.
 */

import { describe, expect, it } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import { StagingReporter } from "../reporter.ts";
import type { CheckResult } from "../types.ts";

describe("Staging Reporter", () => {
  const testOutputDir = path.resolve("scripts/staging-verify/scratch/test-report-out");

  it("aggregates checks, stages, and outputs results.json and results.md", () => {
    const reporter = new StagingReporter(testOutputDir);

    reporter.beginStage("golden");
    const check1: CheckResult = {
      checkNumber: 1,
      name: "clean_traffic.pcap",
      stage: "golden",
      status: "PASS",
      durationMs: 120,
      sourceCitation: "GoldenPcapFixturesIntegrationTest",
      summary: "Passed with 0 threats",
    };
    reporter.recordCheck(check1);

    const check2: CheckResult = {
      checkNumber: 2,
      name: "syn_port_scan.pcap",
      stage: "golden",
      status: "FAIL",
      durationMs: 250,
      sourceCitation: "GoldenPcapFixturesIntegrationTest",
      summary: "Expected 1 threat",
      failure: {
        errorMessage: "Threat count mismatch: expected 1, got 0",
        reproductionSteps: ["Upload file", "Inspect"],
      },
    };
    reporter.recordCheck(check2);
    const stageRes = reporter.endStage();

    expect(stageRes.passedCount).toBe(1);
    expect(stageRes.failedCount).toBe(1);
    expect(stageRes.checks.length).toBe(2);

    const summary = reporter.generateSummary("http://localhost:10000", "http://localhost:8080");
    expect(summary.totalChecks).toBe(2);
    expect(summary.passedCount).toBe(1);
    expect(summary.failedCount).toBe(1);
    expect(summary.success).toBe(false);

    const { jsonPath, mdPath } = reporter.saveReports(summary);
    expect(fs.existsSync(jsonPath)).toBe(true);
    expect(fs.existsSync(mdPath)).toBe(true);

    const jsonContent = JSON.parse(fs.readFileSync(jsonPath, "utf-8"));
    expect(jsonContent.totalChecks).toBe(2);

    const mdContent = fs.readFileSync(mdPath, "utf-8");
    expect(mdContent).toContain("Velorix Sentinel — Staging Verification Run Results");
    expect(mdContent).toContain("clean_traffic.pcap");
    expect(mdContent).toContain("Failure Diagnostics & Reproduction");
    expect(mdContent).toContain("Threat count mismatch");

    // Clean up
    fs.rmSync(testOutputDir, { recursive: true, force: true });
  });
});
