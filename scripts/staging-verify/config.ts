/**
 * Environment configuration loader and validator for Staging Verification.
 * Reads environment variables and fails fast with exact instructions if missing.
 */

import type { StagingConfig } from "./types.ts";

export class ConfigValidationError extends Error {
  constructor(public readonly missingVars: string[]) {
    super(
      `Missing required staging environment variables:\n` +
        missingVars.map((v) => `  - ${v}`).join("\n") +
        `\n\nPlease set these variables in your environment or in .env.staging.` +
        `\nExample:\n` +
        `  STAGING_API_URL=https://velorix-sentinel-staging.onrender.com\n` +
        `  STAGING_FRONTEND_URL=https://staging--velorixsentinel.netlify.app\n` +
        `  STAGING_TEST_EMAIL=staging.tester@example.com\n` +
        `  STAGING_TEST_PASSWORD=SuperSecureStagingPassword#2026!\n`,
    );
    this.name = "ConfigValidationError";
  }
}

export interface LoadConfigOptions {
  env?: NodeJS.ProcessEnv;
  skipUi?: boolean;
}

export function loadConfig(optionsOrEnv?: NodeJS.ProcessEnv | LoadConfigOptions): StagingConfig {
  let env: NodeJS.ProcessEnv = process.env;
  let forceSkipUi: boolean | undefined;

  if (optionsOrEnv) {
    if (
      "apiUrl" in optionsOrEnv ||
      "STAGING_API_URL" in optionsOrEnv ||
      typeof (optionsOrEnv as NodeJS.ProcessEnv)[Symbol.iterator] === "function"
    ) {
      env = optionsOrEnv as NodeJS.ProcessEnv;
    } else {
      const opts = optionsOrEnv as LoadConfigOptions;
      if (opts.env) env = opts.env;
      if (opts.skipUi !== undefined) forceSkipUi = opts.skipUi;
    }
  }
  const missing: string[] = [];

  const apiUrl = env.STAGING_API_URL?.trim();
  if (!apiUrl) missing.push("STAGING_API_URL");

  const frontendUrl = env.STAGING_FRONTEND_URL?.trim() || "http://localhost:5173";
  // Note: if user did not set STAGING_FRONTEND_URL, we default to localhost:5173 for local mock mode,
  // but if running with live flags we check it.
  if (!env.STAGING_FRONTEND_URL?.trim() && env.STAGING_REQUIRE_FRONTEND === "true") {
    missing.push("STAGING_FRONTEND_URL");
  }

  const testEmail = env.STAGING_TEST_EMAIL?.trim();
  if (!testEmail) missing.push("STAGING_TEST_EMAIL");

  const testPassword = env.STAGING_TEST_PASSWORD?.trim();
  if (!testPassword) missing.push("STAGING_TEST_PASSWORD");

  if (missing.length > 0) {
    throw new ConfigValidationError(missing);
  }

  const timeoutMs = parseInt(env.STAGING_TIMEOUT_MS || "60000", 10);
  const skipUi = forceSkipUi !== undefined ? forceSkipUi : env.STAGING_SKIP_UI === "true";

  return {
    apiUrl: apiUrl!.replace(/\/+$/, ""),
    frontendUrl: frontendUrl.replace(/\/+$/, ""),
    testEmail: testEmail!,
    testPassword: testPassword!,
    testEmail2: env.STAGING_TEST_EMAIL_2?.trim(),
    testPassword2: env.STAGING_TEST_PASSWORD_2?.trim(),
    unverifiedEmail: env.STAGING_UNVERIFIED_EMAIL?.trim(),
    unverifiedPassword: env.STAGING_UNVERIFIED_PASSWORD?.trim(),
    skipUi,
    timeoutMs: Number.isNaN(timeoutMs) ? 60000 : timeoutMs,
  };
}
