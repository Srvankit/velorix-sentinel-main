/**
 * Stage 5: Abuse & Rate-Limiting Verification (Check 15).
 * MUST RUN LAST: Temporarily throttles the tester's IP address.
 * Sends a burst of login requests to trigger 429 Too Many Requests + Retry-After,
 * waits out the cooldown period, and confirms normal authentication is restored.
 */

import type { CheckResult, StagingConfig } from "../types.ts";
import type { StagingApiClient } from "../client.ts";

export async function runAbuseStage(
  client: StagingApiClient,
  config: StagingConfig,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];
  const start = Date.now();

  console.warn(
    "\n    ⚠️ WARNING: The abuse stage will intentionally burst requests to trip the IP rate limit.",
  );
  console.warn("    This will temporarily throttle your client IP on the staging server.\n");

  try {
    const burstEmail = "rate.limit.test@velorix.test";
    const burstPassword = "DummyPassword123!";
    let retryAfterSeconds = 60;
    let rateLimited = false;
    let attemptsCount = 0;

    // Send rapid login requests until 429 is encountered (capacity is 5 per 1 minute)
    console.log(
      "    Sending burst login requests to trigger 429 rate limit (capacity is 5 req/min)...",
    );
    for (let i = 1; i <= 8; i++) {
      attemptsCount++;
      const res = await client.login(burstEmail, burstPassword);

      if (res.status === 429) {
        rateLimited = true;
        const retryHeader = res.headers.get("retry-after");
        if (retryHeader) {
          const parsed = parseInt(retryHeader, 10);
          if (!Number.isNaN(parsed) && parsed > 0) {
            retryAfterSeconds = parsed;
          }
        }
        console.log(
          `    429 Too Many Requests received on attempt ${i}! Retry-After: ${retryAfterSeconds}s`,
        );
        break;
      }

      // Small 50ms pause between burst attempts
      await new Promise((r) => setTimeout(r, 50));
    }

    if (!rateLimited) {
      throw new Error(
        `Failed to trigger 429 rate limit after ${attemptsCount} rapid login attempts!`,
      );
    }

    // Wait out Retry-After duration + 3 seconds safety margin
    const waitSeconds = Math.min(retryAfterSeconds + 3, 75);
    console.log(`    Waiting out rate limit window (${waitSeconds}s cooldown)...`);
    await new Promise((r) => setTimeout(r, waitSeconds * 1000));

    // Verify legitimate login works again after cooldown
    console.log("    Attempting valid login after cooldown...");
    const postCooldownRes = await client.login(config.testEmail, config.testPassword);

    if (postCooldownRes.status !== 200) {
      throw Object.assign(
        new Error(`Login failed after rate-limit cooldown (status ${postCooldownRes.status})`),
        {
          requestMethod: "POST",
          requestUrl: "/api/v1/auth/login",
          responseStatus: postCooldownRes.status,
          responseBodySnippet: client.redact(postCooldownRes.bodyText),
        },
      );
    }

    results.push({
      checkNumber: 15,
      name: "Login burst rate-limiting (429 Too Many Requests + Retry-After) and recovery",
      stage: "abuse",
      status: "PASS",
      durationMs: Date.now() - start,
      sourceCitation: "RateLimitingFilter.java (login-ip-capacity: 5, Retry-After header)",
      summary: `HTTP 429 Too Many Requests triggered after ${attemptsCount} attempts. Retry-After header: ${retryAfterSeconds}s. Successful login restored after cooldown.`,
      metadata: {
        burstAttempts: attemptsCount,
        retryAfterSeconds,
        recoveryStatus: postCooldownRes.status,
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
      checkNumber: 15,
      name: "Login burst rate-limiting (429 Too Many Requests + Retry-After) and recovery",
      stage: "abuse",
      status: "FAIL",
      durationMs: Date.now() - start,
      sourceCitation: "RateLimitingFilter.java (login-ip-capacity: 5, Retry-After header)",
      summary: `Abuse rate-limit check failed: ${error.message}`,
      failure: {
        requestMethod: error.requestMethod,
        requestUrl: error.requestUrl,
        responseStatus: error.responseStatus,
        responseBodySnippet: error.responseBodySnippet,
        errorMessage: error.message,
        reproductionSteps: [
          "Send 6 rapid login requests from the same IP -> expect HTTP 429 on 6th attempt",
          "Inspect Retry-After response header",
          "Wait out Retry-After window",
          "Attempt valid login -> expect HTTP 200 OK",
        ],
      },
    });
  }

  return results;
}
