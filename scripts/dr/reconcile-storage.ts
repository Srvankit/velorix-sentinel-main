#!/usr/bin/env bun
/**
 * Velorix Sentinel — Storage Reconciliation Tool (Read-Only).
 * Task 8.3: Disaster Recovery Capture Storage Integrity Reconciler.
 *
 * Compares capture metadata in PostgreSQL (uploaded_files table) against
 * physical objects in the S3-compatible cloud storage bucket (Supabase Storage / S3).
 *
 * READ-ONLY: Never deletes or modifies any file in storage or database.
 *
 * USAGE:
 *   bun run scripts/dr/reconcile-storage.ts [options]
 *
 * ENVIRONMENT VARIABLES (Identical to backend application contracts):
 *   Database:
 *     DR_DB_HOST / SUPABASE_DB_HOST
 *     DR_DB_PORT / SUPABASE_DB_PORT (default: 5432)
 *     DR_DB_NAME / SUPABASE_DB_NAME (default: postgres)
 *     DR_DB_USER / SUPABASE_DB_USERNAME
 *     DR_DB_PASSWORD / PGPASSWORD / SUPABASE_DB_PASSWORD
 *
 *   S3 / Supabase Storage:
 *     S3_BUCKET_NAME / SUPABASE_STORAGE_BUCKET (default: velorix-captures)
 *     S3_ENDPOINT / SUPABASE_STORAGE_S3_ENDPOINT
 *     AWS_REGION / S3_REGION (default: us-east-1)
 *     AWS_ACCESS_KEY_ID / S3_ACCESS_KEY
 *     AWS_SECRET_ACCESS_KEY / S3_SECRET_KEY
 *     S3_PREFIX (default: captures/)
 */

import { execSync } from "node:child_process";
import { parseArgs } from "node:util";

export interface StorageReconciliationReport {
  timestamp: string;
  bucket: string;
  prefix: string;
  totalDbRecords: number;
  totalS3Objects: number;
  matchedCount: number;
  missingInS3: string[];
  orphanedInS3: string[];
  durationMs: number;
}

function resolveConfig() {
  const env = process.env;

  const bucket = env.S3_BUCKET_NAME || env.SUPABASE_STORAGE_BUCKET || "velorix-captures";
  const region = env.AWS_REGION || env.S3_REGION || "us-east-1";
  const endpoint = env.S3_ENDPOINT || env.SUPABASE_STORAGE_S3_ENDPOINT || "";
  const accessKey = env.AWS_ACCESS_KEY_ID || env.S3_ACCESS_KEY || "";
  const secretKey = env.AWS_SECRET_ACCESS_KEY || env.S3_SECRET_KEY || "";
  const prefix = env.S3_PREFIX !== undefined ? env.S3_PREFIX : "captures/";

  const dbHost = env.DR_DB_HOST || env.SUPABASE_DB_HOST || "localhost";
  const dbPort = env.DR_DB_PORT || env.SUPABASE_DB_PORT || "5432";
  const dbName = env.DR_DB_NAME || env.SUPABASE_DB_NAME || "postgres";
  const dbUser = env.DR_DB_USER || env.SUPABASE_DB_USERNAME || "postgres";
  const dbPassword = env.DR_DB_PASSWORD || env.PGPASSWORD || env.SUPABASE_DB_PASSWORD || "";

  return {
    bucket,
    region,
    endpoint,
    accessKey,
    secretKey,
    prefix,
    db: { host: dbHost, port: dbPort, name: dbName, user: dbUser, password: dbPassword },
  };
}

/**
 * Fetches stored_file_name records from uploaded_files table using psql.
 */
export function fetchDatabaseStorageKeys(config: ReturnType<typeof resolveConfig>): string[] {
  const query = `SELECT stored_file_name FROM uploaded_files WHERE stored_file_name IS NOT NULL;`;
  const env = { ...process.env, PGPASSWORD: config.db.password };

  try {
    const output = execSync(
      `psql -h "${config.db.host}" -p "${config.db.port}" -U "${config.db.user}" -d "${config.db.name}" -t -A -c "${query}"`,
      { env, encoding: "utf-8", stdio: ["pipe", "pipe", "pipe"] },
    );

    return output
      .split("\n")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  } catch (err: unknown) {
    const error = err as Error;
    console.warn(`⚠️ Warning: Could not query database directly (${error.message.trim()}).`);
    return [];
  }
}

/**
 * Fetches object keys from S3 using AWS CLI if installed.
 */
export function fetchS3ObjectKeys(config: ReturnType<typeof resolveConfig>): string[] {
  if (!config.endpoint && !config.accessKey) {
    console.warn("⚠️ S3 credentials or endpoint not set. S3 listing skipped.");
    return [];
  }

  try {
    let cmd = `aws s3api list-objects-v2 --bucket "${config.bucket}" --query "Contents[].Key" --output json`;
    if (config.endpoint) {
      cmd += ` --endpoint-url "${config.endpoint}"`;
    }
    if (config.prefix) {
      cmd += ` --prefix "${config.prefix}"`;
    }

    const env = {
      ...process.env,
      AWS_ACCESS_KEY_ID: config.accessKey,
      AWS_SECRET_ACCESS_KEY: config.secretKey,
      AWS_DEFAULT_REGION: config.region,
    };

    const output = execSync(cmd, { env, encoding: "utf-8", stdio: ["pipe", "pipe", "pipe"] });
    const parsed = JSON.parse(output || "[]");
    if (Array.isArray(parsed)) {
      return parsed.filter(Boolean);
    }
    return [];
  } catch (err: unknown) {
    const error = err as Error;
    console.warn(`⚠️ Warning: Could not query S3 bucket (${error.message.trim()}).`);
    return [];
  }
}

/**
 * Pure comparison logic between database keys and S3 keys.
 */
export function reconcileStorageKeys(
  dbStoredFileNames: string[],
  s3ObjectKeys: string[],
  prefix = "captures/",
): {
  matchedCount: number;
  missingInS3: string[];
  orphanedInS3: string[];
} {
  // Database stored_file_name values resolve to S3 keys as: prefix + stored_file_name
  const expectedS3Keys = new Set(
    dbStoredFileNames.map((name) => {
      const clean = name.replace(/^[/\\]+/, "");
      return prefix ? `${prefix}${clean}` : clean;
    }),
  );

  const actualS3Keys = new Set(s3ObjectKeys);

  const missingInS3: string[] = [];
  let matchedCount = 0;

  for (const exp of expectedS3Keys) {
    if (actualS3Keys.has(exp)) {
      matchedCount++;
    } else {
      missingInS3.push(exp);
    }
  }

  const orphanedInS3: string[] = [];
  for (const act of actualS3Keys) {
    // Only consider objects under the designated prefix
    if (!prefix || act.startsWith(prefix)) {
      if (!expectedS3Keys.has(act)) {
        orphanedInS3.push(act);
      }
    }
  }

  return { matchedCount, missingInS3, orphanedInS3 };
}

async function main() {
  const start = Date.now();
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      help: { type: "boolean", short: "h", default: false },
    },
  });

  if (values.help) {
    console.log(`
Velorix Sentinel — Storage Reconciliation Tool (Read-Only)
Usage:
  bun run scripts/dr/reconcile-storage.ts
  node scripts/dr/reconcile-storage.ts
`);
    process.exit(0);
  }

  console.log(`==============================================================================`);
  console.log(`🛡️  VELORIX SENTINEL — STORAGE RECONCILIATION AUDIT (READ-ONLY)`);
  console.log(`==============================================================================`);

  const config = resolveConfig();
  console.log(`Bucket:         ${config.bucket}`);
  console.log(`Prefix:         ${config.prefix}`);
  console.log(`S3 Endpoint:    ${config.endpoint || "(default AWS)"}`);
  console.log(`Database Host:  ${config.db.host}:${config.db.port}/${config.db.name}`);
  console.log(`------------------------------------------------------------------------------`);

  console.log("Querying database uploaded_files records...");
  const dbKeys = fetchDatabaseStorageKeys(config);
  console.log(`Found ${dbKeys.length} stored file records in database.`);

  console.log("Querying S3 object storage keys...");
  const s3Keys = fetchS3ObjectKeys(config);
  console.log(`Found ${s3Keys.length} objects in storage bucket.`);

  const comparison = reconcileStorageKeys(dbKeys, s3Keys, config.prefix);

  const durationMs = Date.now() - start;

  console.log(`\n------------------------------------------------------------------------------`);
  console.log(`📊 RECONCILIATION RESULTS`);
  console.log(`------------------------------------------------------------------------------`);
  console.log(`Total Database Captures:  ${dbKeys.length}`);
  console.log(`Total S3 Objects:         ${s3Keys.length}`);
  console.log(`Matched (Valid in Both):  ${comparison.matchedCount}`);
  console.log(`Missing in S3 (Critical): ${comparison.missingInS3.length}`);
  console.log(`Orphaned in S3 (Unlinked):${comparison.orphanedInS3.length}`);
  console.log(`Duration:                 ${durationMs}ms`);

  if (comparison.missingInS3.length > 0) {
    console.log(`\n❌ CRITICAL: Captures in database missing from physical storage:`);
    comparison.missingInS3.forEach((k) => console.log(`   - ${k}`));
  }

  if (comparison.orphanedInS3.length > 0) {
    console.log(`\n⚠️ NOTICE: S3 objects present with no corresponding database record:`);
    comparison.orphanedInS3.forEach((k) => console.log(`   - ${k}`));
  }

  console.log(`==============================================================================`);

  if (comparison.missingInS3.length > 0) {
    process.exit(1);
  }
}

// Only execute main when run directly
if (import.meta.main || process.argv[1]?.endsWith("reconcile-storage.ts")) {
  main().catch((err) => {
    console.error("Fatal error during storage reconciliation:", err);
    process.exit(1);
  });
}
