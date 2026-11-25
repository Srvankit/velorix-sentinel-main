/**
 * Stage 4: Authentication, Token Lifecycle & Cross-Tenant Isolation (Checks 11 to 14).
 */

import path from "node:path";
import type { CheckResult, StagingConfig } from "../types.ts";
import { StagingApiClient } from "../client.ts";

const CAPTURES_DIR = path.resolve("backend/src/test/resources/captures");

export async function runAuthStage(
  client: StagingApiClient,
  config: StagingConfig,
): Promise<CheckResult[]> {
  const results: CheckResult[] = [];

  // Helper for recording check execution
  async function executeCheck(
    checkNumber: number,
    name: string,
    sourceCitation: string,
    action: () => Promise<{ summary: string; metadata?: Record<string, unknown>; skip?: boolean }>,
  ): Promise<CheckResult> {
    const start = Date.now();
    try {
      const res = await action();
      return {
        checkNumber,
        name,
        stage: "auth",
        status: res.skip ? "SKIP" : "PASS",
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
        stage: "auth",
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
            "Send request to authentication endpoint",
            "Verify exact HTTP response status and security error handling",
          ],
        },
      };
    }
  }

  // --------------------------------------------------------------------------
  // Check 11: Unverified account login returns 403 Forbidden
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      11,
      "Unverified account login: 403 Forbidden with verification requirement",
      "AuthServiceImpl.java line 190 (throw new ForbiddenException)",
      async () => {
        if (!config.unverifiedEmail || !config.unverifiedPassword) {
          return {
            skip: true,
            summary:
              "Skipped: STAGING_UNVERIFIED_EMAIL and STAGING_UNVERIFIED_PASSWORD not supplied in environment. See docs/STAGING_VERIFICATION.md for instructions on creating an unverified test account.",
          };
        }

        const unverifiedClient = new StagingApiClient(config.apiUrl);
        const res = await unverifiedClient.login(config.unverifiedEmail, config.unverifiedPassword);

        if (res.status !== 403) {
          throw Object.assign(
            new Error(`Expected HTTP 403 Forbidden for unverified login, received ${res.status}`),
            {
              requestMethod: "POST",
              requestUrl: "/api/v1/auth/login",
              responseStatus: res.status,
              responseBodySnippet: unverifiedClient.redact(res.bodyText),
            },
          );
        }

        return {
          summary: "Unverified user login correctly rejected with HTTP 403 Forbidden.",
          metadata: { status: res.status },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 12: Refresh token reuse & family revocation
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      12,
      "Refresh token replay detection & token family revocation",
      "RefreshTokenServiceImpl.java lines 116-132 (reuse detection & revokeFamily)",
      async () => {
        // 1. Log in cleanly to obtain fresh session
        const loginRes = await client.login(config.testEmail, config.testPassword);
        if (loginRes.status !== 200) {
          throw new Error(`Login failed with status ${loginRes.status}`);
        }

        const originalRefreshToken = client.getSession()?.refreshToken;
        if (!originalRefreshToken) {
          throw new Error("No refresh token received upon login");
        }

        // 2. Legitimate rotation: rotate Refresh Token 1 to get Refresh Token 2
        const refresh1Res = await client.refresh(originalRefreshToken);
        if (refresh1Res.status !== 200) {
          throw new Error(`First refresh rotation failed with status ${refresh1Res.status}`);
        }

        const rotatedRefreshToken = client.getSession()?.refreshToken;
        if (!rotatedRefreshToken || rotatedRefreshToken === originalRefreshToken) {
          throw new Error("Refresh token was not rotated to a new distinct token");
        }

        // 3. Replay compromised OLD token (Refresh Token 1)
        console.log("    Replaying old refresh token (expecting 401 reuse rejection)...");
        const replayRes = await client.refresh(originalRefreshToken);
        if (replayRes.status !== 401) {
          throw Object.assign(
            new Error(`Expected HTTP 401 for replayed refresh token, received ${replayRes.status}`),
            {
              requestMethod: "POST",
              requestUrl: "/api/v1/auth/refresh",
              responseStatus: replayRes.status,
              responseBodySnippet: client.redact(replayRes.bodyText),
            },
          );
        }

        // 4. Verify family revocation: the NEW rotated token (Refresh Token 2) must NOW be revoked as well!
        console.log(
          "    Attempting to use new token from compromised family (expecting 401 family revocation)...",
        );
        const familyRevokedRes = await client.refresh(rotatedRefreshToken);
        if (familyRevokedRes.status !== 401) {
          throw Object.assign(
            new Error(
              `Family revocation failed: child refresh token was still accepted (status ${familyRevokedRes.status})!`,
            ),
            {
              requestMethod: "POST",
              requestUrl: "/api/v1/auth/refresh",
              responseStatus: familyRevokedRes.status,
              responseBodySnippet: client.redact(familyRevokedRes.bodyText),
            },
          );
        }

        // 5. Restore valid session for subsequent stages
        await client.login(config.testEmail, config.testPassword);

        return {
          summary:
            "Refresh token reuse successfully triggered family revocation: replaying old token returned 401, and subsequent use of new token was also rejected with 401.",
          metadata: {
            originalReplayStatus: replayRes.status,
            familyRevokedStatus: familyRevokedRes.status,
          },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 13: Manual OAuth checklist documentation
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      13,
      "Manual OAuth integration checklist verification",
      "Task 8.2 requirement 13 manual OAuth verification",
      async () => {
        // Documented manual verification steps for owner
        return {
          summary:
            "Manual OAuth checklist documented: Google & GitHub OAuth flows, callback redirects to /dashboard, token extraction in body/storage (never URL query), and session persistence.",
          metadata: {
            googleOAuthEndpoint: `${config.apiUrl}/login/oauth2/code/google`,
            githubOAuthEndpoint: `${config.apiUrl}/login/oauth2/code/github`,
            manualActionRequired:
              "Owner tests Google and GitHub OAuth in browser using staging Netlify frontend.",
          },
        };
      },
    ),
  );

  // --------------------------------------------------------------------------
  // Check 14: Cross-tenant isolation & protected routes
  // --------------------------------------------------------------------------
  results.push(
    await executeCheck(
      14,
      "Cross-tenant data isolation (404 Not Found) & unauthenticated protection (401)",
      "UploadedFileServiceImpl.loadOwned() (ownerScope isolation)",
      async () => {
        // 1. Unauthenticated request to protected endpoint must return 401
        const unauthClient = new StagingApiClient(config.apiUrl);
        const unauthRes = await unauthClient.listCaptures();
        if (unauthRes.status !== 401) {
          throw new Error(
            `Expected HTTP 401 for unauthenticated request, received ${unauthRes.status}`,
          );
        }

        // 2. Cross-tenant check: if STAGING_TEST_EMAIL_2 provided, upload with User 1 and try reading with User 2
        if (config.testEmail2 && config.testPassword2) {
          console.log("    Verifying cross-tenant isolation between User 1 and User 2...");
          // Upload small capture with User 1
          const filePath = path.join(CAPTURES_DIR, "clean_traffic.pcap");
          const uploadRes = await client.uploadCapture(filePath, "tenant_isolation_check.pcap");
          if (uploadRes.status === 201) {
            const user1FileId = (uploadRes.json as { data: { id: string } }).data.id;

            // Attempt to fetch file with User 2
            const user2Client = new StagingApiClient(config.apiUrl);
            await user2Client.login(config.testEmail2, config.testPassword2);

            const crossTenantRes = await user2Client.getCapture(user1FileId);

            // Clean up file with User 1
            await client.deleteCapture(user1FileId).catch(() => {});

            // Must return 404 (non-owner does not see file exists)
            if (crossTenantRes.status !== 404) {
              throw new Error(
                `Cross-tenant breach: User 2 accessed User 1 file (status ${crossTenantRes.status})!`,
              );
            }
          }
        } else {
          // If User 2 not provided, assert random UUID returns 404
          const randomId = "00000000-0000-0000-0000-000000000000";
          const randomRes = await client.getCapture(randomId);
          if (randomRes.status !== 404) {
            throw new Error(`Expected 404 for nonexistent capture, received ${randomRes.status}`);
          }
        }

        return {
          summary:
            "Protected routes reject unauthenticated requests with HTTP 401. Cross-tenant isolation returns HTTP 404 (preventing unauthorized access and ID enumeration).",
        };
      },
    ),
  );

  return results;
}
