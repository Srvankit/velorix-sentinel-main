/**
 * Test result aggregator and reporter.
 * Produces machine-readable results.json and human-readable results.md
 * with redacted request/response snippets and reproduction steps.
 */

import fs from "node:fs";
import path from "node:path";
import type { CheckResult, HarnessRunSummary, StageName, StageResult } from "./types.ts";

export class StagingReporter {
  private stages: StageResult[] = [];
  private currentStage: StageName = "golden";
  private currentStageChecks: CheckResult[] = [];
  private stageStartedAt: number = Date.now();
  private readonly suiteStartedAt: number = Date.now();

  constructor(private readonly outputDir: string) {
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
  }

  public beginStage(stage: StageName) {
    this.currentStage = stage;
    this.currentStageChecks = [];
    this.stageStartedAt = Date.now();
    console.log(`\n======================================================================`);
    console.log(`>>> STARTING STAGE: [${stage.toUpperCase()}]`);
    console.log(`======================================================================`);
  }

  public recordCheck(result: CheckResult) {
    this.currentStageChecks.push(result);
    const symbol = result.status === "PASS" ? "✅" : result.status === "FAIL" ? "❌" : "⚠️";
    console.log(
      `  ${symbol} Check ${result.checkNumber}: ${result.name} [${result.status}] (${result.durationMs}ms)`,
    );
    if (result.status === "FAIL" && result.failure) {
      console.error(`     Error: ${result.failure.errorMessage}`);
    }
  }

  public endStage(): StageResult {
    const durationMs = Date.now() - this.stageStartedAt;
    const passed = this.currentStageChecks.filter((c) => c.status === "PASS").length;
    const failed = this.currentStageChecks.filter((c) => c.status === "FAIL").length;
    const skipped = this.currentStageChecks.filter((c) => c.status === "SKIP").length;

    const stageResult: StageResult = {
      stage: this.currentStage,
      durationMs,
      passedCount: passed,
      failedCount: failed,
      skippedCount: skipped,
      checks: [...this.currentStageChecks],
    };

    this.stages.push(stageResult);
    console.log(
      `--- Stage [${this.currentStage.toUpperCase()}] Complete: ${passed} Passed, ${failed} Failed, ${skipped} Skipped in ${durationMs}ms ---\n`,
    );
    return stageResult;
  }

  public generateSummary(apiUrl: string, frontendUrl?: string): HarnessRunSummary {
    const totalDurationMs = Date.now() - this.suiteStartedAt;
    let totalChecks = 0;
    let passedCount = 0;
    let failedCount = 0;
    let skippedCount = 0;

    for (const stage of this.stages) {
      totalChecks += stage.checks.length;
      passedCount += stage.passedCount;
      failedCount += stage.failedCount;
      skippedCount += stage.skippedCount;
    }

    return {
      timestamp: new Date().toISOString(),
      apiUrl,
      frontendUrl,
      durationMs: totalDurationMs,
      totalChecks,
      passedCount,
      failedCount,
      skippedCount,
      success: failedCount === 0,
      stages: this.stages,
    };
  }

  public saveReports(summary: HarnessRunSummary): { jsonPath: string; mdPath: string } {
    const jsonPath = path.join(this.outputDir, "results.json");
    const mdPath = path.join(this.outputDir, "results.md");

    // 1. Write results.json
    fs.writeFileSync(jsonPath, JSON.stringify(summary, null, 2), "utf-8");

    // 2. Build results.md
    let md = `# Velorix Sentinel — Staging Verification Run Results\n\n`;
    md += `**Timestamp:** \`${summary.timestamp}\`  \n`;
    md += `**API Target:** \`${summary.apiUrl}\`  \n`;
    if (summary.frontendUrl) {
      md += `**Frontend Target:** \`${summary.frontendUrl}\`  \n`;
    }
    md += `**Overall Verdict:** ${summary.success ? "🟢 **PASS**" : "🔴 **FAIL**"}  \n`;
    md += `**Total Duration:** \`${(summary.durationMs / 1000).toFixed(2)}s\`  \n`;
    md += `**Score:** \`${summary.passedCount} Passed\`, \`${summary.failedCount} Failed\`, \`${summary.skippedCount} Skipped\` out of \`${summary.totalChecks} Checks\`\n\n`;

    md += `## 1. Stage Summary\n\n`;
    md += `| Stage | Status | Duration | Passed | Failed | Skipped |\n`;
    md += `| :--- | :---: | :---: | :---: | :---: | :---: |\n`;
    for (const stage of summary.stages) {
      const stageVerdict = stage.failedCount > 0 ? "❌ FAIL" : "✅ PASS";
      md += `| **${stage.stage}** | ${stageVerdict} | ${(stage.durationMs / 1000).toFixed(2)}s | ${stage.passedCount} | ${stage.failedCount} | ${stage.skippedCount} |\n`;
    }
    md += `\n---\n\n`;

    md += `## 2. Check Execution Matrix\n\n`;
    md += `| # | Check Name | Stage | Verdict | Duration | Cited Source |\n`;
    md += `| :---: | :--- | :---: | :---: | :---: | :--- |\n`;

    for (const stage of summary.stages) {
      for (const check of stage.checks) {
        const statusBadge =
          check.status === "PASS" ? "✅ PASS" : check.status === "FAIL" ? "❌ FAIL" : "⚠️ SKIP";
        md += `| **${check.checkNumber}** | ${check.name} | \`${check.stage}\` | ${statusBadge} | ${check.durationMs}ms | \`${check.sourceCitation}\` |\n`;
      }
    }
    md += `\n---\n\n`;

    // 3. Detailed Failure Section (if any failures)
    const failedChecks = summary.stages.flatMap((s) => s.checks).filter((c) => c.status === "FAIL");
    if (failedChecks.length > 0) {
      md += `## 3. Failure Diagnostics & Reproduction\n\n`;
      for (const check of failedChecks) {
        md += `### Check ${check.checkNumber}: ${check.name}\n\n`;
        md += `- **Stage:** \`${check.stage}\`\n`;
        md += `- **Error:** \`${check.failure?.errorMessage}\`\n\n`;

        if (check.failure?.requestMethod && check.failure?.requestUrl) {
          md += `#### HTTP Request\n\`\`\`http\n${check.failure.requestMethod} ${check.failure.requestUrl}\n`;
          if (check.failure.requestBodySnippet) {
            md += `\n${check.failure.requestBodySnippet}\n`;
          }
          md += `\`\`\`\n\n`;
        }

        if (check.failure?.responseStatus) {
          md += `#### HTTP Response\n\`\`\`http\nHTTP/1.1 ${check.failure.responseStatus}\n`;
          if (check.failure.responseBodySnippet) {
            md += `\n${check.failure.responseBodySnippet}\n`;
          }
          md += `\`\`\`\n\n`;
        }

        if (check.failure?.reproductionSteps && check.failure.reproductionSteps.length > 0) {
          md += `#### Reproduction Steps\n`;
          check.failure.reproductionSteps.forEach((step, idx) => {
            md += `${idx + 1}. ${step}\n`;
          });
          md += `\n`;
        }
      }
    } else {
      md += `## 3. Failure Diagnostics\n\n`;
      md += `*No failures encountered. All automated checks succeeded cleanly.*\n\n`;
    }

    fs.writeFileSync(mdPath, md, "utf-8");
    return { jsonPath, mdPath };
  }
}
