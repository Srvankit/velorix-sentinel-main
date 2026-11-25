#!/usr/bin/env bun
/**
 * Velorix Sentinel — Staging Golden PCAP Verification Harness CLI.
 * Task 8.2: Automated staging verification suite covering all 16 staging checks across 6 stages.
 *
 * Usage:
 *   bun run scripts/staging-verify/index.ts [options]
 *
 * Options:
 *   --stage <name>       Stage to execute: golden | large | quota | auth | abuse | persistence | all (default: all)
 *   --phase <phase>      Persistence phase: before | after (used when --stage persistence)
 *   --skip-ui            Skip Playwright browser UI assertions (API-only validation)
 *   --output <dir>       Custom directory for output reports (default: scripts/staging-verify/output)
 *   --help, -h           Show this help message
 */

import path from "node:path";
import { parseArgs } from "node:util";
import { loadConfig } from "./config.ts";
import { StagingApiClient } from "./client.ts";
import { StagingUiVerifier } from "./ui-verifier.ts";
import { StagingReporter } from "./reporter.ts";
import { runGoldenStage } from "./stages/golden.ts";
import { runLargeStage } from "./stages/large.ts";
import { runQuotaStage } from "./stages/quota.ts";
import { runAuthStage } from "./stages/auth.ts";
import { runAbuseStage } from "./stages/abuse.ts";
import { runPersistenceStage } from "./stages/persistence.ts";
import type { PersistencePhase, StageName } from "./types.ts";

function printHelp() {
  console.log(`
Velorix Sentinel — Staging Verification Harness CLI
Task 8.2: End-to-End Staging Pre-Flight & Golden PCAP Verification

USAGE:
  bun run scripts/staging-verify/index.ts [options]
  node scripts/staging-verify/index.ts [options]

OPTIONS:
  --stage <name>    Stage to run:
                      golden      (Checks 1-8: 6 golden fixtures, 409 dedup, stuck-run cleanup)
                      large       (Check 9: 101MB rejection, 98MB processing, actuator health)
                      quota       (Check 10: 10-file upload limit, 11th 409 rejection, re-upload)
                      auth        (Checks 11-14: unverified 403, refresh reuse 401, OAuth checklist, cross-tenant 404)
                      abuse       (Check 15: 5 login burst, 6th 429 rate-limit + Retry-After, cooldown)
                      persistence (Check 16: Two-phase storage persistence across restart)
                      all         (Default: runs all stages sequentially)
  --phase <phase>   Persistence stage phase: before | after
  --skip-ui         Skip headless browser UI visual assertions (API inspection only)
  --output <dir>    Output directory for results.json and results.md (default: scripts/staging-verify/output)
  --help, -h        Print this help message and exit

REQUIRED ENVIRONMENT VARIABLES:
  STAGING_API_URL        Base URL of the staging backend (e.g. https://api-staging.velorix.net or http://localhost:10000)
  STAGING_TEST_EMAIL     Email for verified primary test user
  STAGING_TEST_PASSWORD  Password for primary test user

OPTIONAL ENVIRONMENT VARIABLES:
  STAGING_FRONTEND_URL        Frontend URL for Playwright checks (default: http://localhost:8080)
  STAGING_TEST_EMAIL_2        Secondary user email (for quota & cross-tenant tests)
  STAGING_TEST_PASSWORD_2     Secondary user password
  STAGING_UNVERIFIED_EMAIL    Unverified user email (for Check 11)
  STAGING_UNVERIFIED_PASSWORD Unverified user password
  STAGING_TIMEOUT_MS          Network timeout in ms (default: 60000)
`);
}

async function main() {
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      stage: { type: "string", default: "all" },
      phase: { type: "string" },
      "skip-ui": { type: "boolean", default: false },
      output: { type: "string", default: path.resolve("scripts/staging-verify/output") },
      help: { type: "boolean", short: "h", default: false },
    },
    allowPositionals: false,
  });

  if (values.help) {
    printHelp();
    process.exit(0);
  }

  const stageArg = (values.stage || "all").toLowerCase() as StageName;
  const phaseArg = values.phase as PersistencePhase | undefined;
  const skipUi = values["skip-ui"] ?? false;
  const outputDir = path.resolve(values.output || "scripts/staging-verify/output");

  console.log(`\n======================================================================`);
  console.log(`🛡️  VELORIX SENTINEL — STAGING VERIFICATION HARNESS`);
  console.log(`   Task 8.2: Golden PCAP & Pre-flight Staging Verification`);
  console.log(`======================================================================`);
  console.log(`Target Stage:    [${stageArg.toUpperCase()}]`);
  if (phaseArg) console.log(`Persistence Phase: [${phaseArg}]`);
  console.log(`UI Assertions:   ${skipUi ? "SKIPPED (--skip-ui)" : "ENABLED"}`);
  console.log(`Report Output:   ${outputDir}\n`);

  // Load and validate environment configuration
  const config = loadConfig({ skipUi });

  // Initialize client and reporter
  const client = new StagingApiClient(config.apiUrl);
  const uiVerifier = new StagingUiVerifier(config);
  const reporter = new StagingReporter(outputDir);

  // Pre-flight check: Backend Health
  console.log(`Checking backend actuator health at ${config.apiUrl}/actuator/health...`);
  try {
    const healthRes = await client.getActuatorHealth();
    if (healthRes.status === 200) {
      console.log(`✅ Actuator health status: UP (HTTP 200)\n`);
    } else {
      console.warn(`⚠️ Actuator returned non-200 status: ${healthRes.status}. Continuing...\n`);
    }
  } catch (err: unknown) {
    const error = err as Error;
    console.error(`❌ Actuator health probe failed: ${error.message}`);
    console.error(`   Ensure backend is running and reachable at ${config.apiUrl}.\n`);
  }

  // Pre-flight authentication
  console.log(`Authenticating primary test account (${config.testEmail})...`);
  const loginRes = await client.login(config.testEmail, config.testPassword);
  if (loginRes.status !== 200) {
    console.error(`❌ Authentication failed with status ${loginRes.status}: ${loginRes.bodyText}`);
    console.error(`   Verify STAGING_TEST_EMAIL and STAGING_TEST_PASSWORD.\n`);
    process.exit(1);
  }
  console.log(`✅ Authenticated successfully. Session token acquired.\n`);

  // Execute stages
  const stagesToRun: Array<{
    name: StageName;
    runner: () => Promise<void>;
  }> = [];

  if (stageArg === "all" || stageArg === "golden") {
    stagesToRun.push({
      name: "golden",
      runner: async () => {
        reporter.beginStage("golden");
        const results = await runGoldenStage(client, uiVerifier, config);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  if (stageArg === "all" || stageArg === "large") {
    stagesToRun.push({
      name: "large",
      runner: async () => {
        reporter.beginStage("large");
        const results = await runLargeStage(client, config);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  if (stageArg === "all" || stageArg === "quota") {
    stagesToRun.push({
      name: "quota",
      runner: async () => {
        reporter.beginStage("quota");
        const results = await runQuotaStage(client, config);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  if (stageArg === "all" || stageArg === "auth") {
    stagesToRun.push({
      name: "auth",
      runner: async () => {
        reporter.beginStage("auth");
        const results = await runAuthStage(client, uiVerifier, config);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  if (stageArg === "all" || stageArg === "abuse") {
    stagesToRun.push({
      name: "abuse",
      runner: async () => {
        reporter.beginStage("abuse");
        const results = await runAbuseStage(client, config);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  if (stageArg === "all" || stageArg === "persistence") {
    stagesToRun.push({
      name: "persistence",
      runner: async () => {
        reporter.beginStage("persistence");
        const results = await runPersistenceStage(client, config, phaseArg);
        for (const res of results) reporter.recordCheck(res);
        reporter.endStage();
      },
    });
  }

  for (const s of stagesToRun) {
    try {
      await s.runner();
    } catch (err: unknown) {
      const error = err as Error;
      console.error(
        `💥 Unexpected error during stage [${s.name}]: ${error.message}\n`,
        error.stack,
      );
    }
  }

  // Generate and save reports
  const summary = reporter.generateSummary(config.apiUrl, config.frontendUrl);
  const { jsonPath, mdPath } = reporter.saveReports(summary);

  console.log(`\n======================================================================`);
  console.log(`📊 HARNESS RUN COMPLETED`);
  console.log(`======================================================================`);
  console.log(
    `Verdict:         ${summary.success ? "🟢 PASS (All checks succeeded)" : "🔴 FAIL (One or more checks failed)"}`,
  );
  console.log(`Total Checks:    ${summary.totalChecks}`);
  console.log(`Passed:          ${summary.passedCount}`);
  console.log(`Failed:          ${summary.failedCount}`);
  console.log(`Skipped:         ${summary.skippedCount}`);
  console.log(`Duration:        ${(summary.durationMs / 1000).toFixed(2)}s`);
  console.log(`JSON Report:     ${jsonPath}`);
  console.log(`Markdown Report: ${mdPath}\n`);

  if (!summary.success) {
    process.exit(1);
  }
}

main().catch((err: unknown) => {
  const error = err as Error;
  console.error(`\n❌ Harness execution fatal error: ${error.message}\n`, error.stack);
  process.exit(1);
});
