#!/usr/bin/env bun
/**
 * Velorix Sentinel — Storage Reconciliation Tool (Read-Only).
 * Task 8.3: Disaster Recovery Capture Storage Integrity Reconciler.
 *
 * Compares active capture metadata in PostgreSQL (uploaded_files table) against
 * physical objects in the S3-compatible cloud storage bucket (Supabase Storage / S3).
 *
 * S3 COMMUNICATION:
 *   Uses @aws-sdk/client-s3 (S3Client, ListObjectsV2Command) to query the bucket
 *   over HTTPS with pagination, supporting Supabase Storage S3 endpoint and path-style access.
 *
 * SOFT-DELETE EXCLUSION:
 *   In Velorix Sentinel, when a user deletes a capture, the binary is deleted from S3
 *   and the database row is marked with upload_status = 'DELETED' (soft delete).
 *   Soft-deleted rows are explicitly excluded from expected storage keys.
 *
 * KEY-BUILDING ALIGNMENT:
 *   Key-building logic matches backend S3CompatibleFileStorageService.java (resolveObjectKey):
 *   - Normalizes backslashes to forward slashes.
 *   - Strips leading forward slashes.
 *   - Rejects directory traversal (..).
 *   - Prepends S3_PREFIX (default: captures/).
 *
 * READ-ONLY: Never deletes or modifies any file in storage or database.
 *
 * USAGE:
 *   bun run scripts/dr/reconcile-storage.ts [options]
 *   node scripts/dr/reconcile-storage.ts [options]
 */

import { execSync } from "node:child_process";
import { parseArgs } from "node:util";
import { ListObjectsV2Command, S3Client } from "@aws-sdk/client-s3";

export interface DbCaptureRecord {
  storedFileName: string;
  uploadStatus: string;
}

export interface StorageReconciliationReport {
  timestamp: string;
  bucket: string;
  prefix: string;
  totalDbRecords: number;
  activeDbRecords: number;
  softDeletedRecords: number;
  totalS3Objects: number;
  matchedCount: number;
  missingInS3: string[];
  orphanedInS3: string[];
  durationMs: number;
}

export function resolveConfig() {
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
 * Resolves storage key to S3 object key matching S3CompatibleFileStorageService.java (resolveObjectKey).
 */
export function resolveS3Key(storageKey: string, prefix = "captures/"): string {
  if (!storageKey || !storageKey.trim()) {
    throw new Error("A storage key is required");
  }
  let cleanKey = storageKey.replace(/\\/g, "/").trim();
  while (cleanKey.startsWith("/")) {
    cleanKey = cleanKey.substring(1);
  }
  if (cleanKey.includes("..")) {
    throw new Error(`Illegal storage key: directory traversal rejected (${storageKey})`);
  }
  if (!prefix || !prefix.trim()) {
    return cleanKey;
  }
  return `${prefix}${cleanKey}`;
}

/**
 * Fetches capture records from PostgreSQL using psql CLI.
 * Returns both active and soft-deleted records for complete visibility.
 */
export function fetchDatabaseRecords(config: ReturnType<typeof resolveConfig>): DbCaptureRecord[] {
  const query = `SELECT stored_file_name, upload_status FROM uploaded_files WHERE stored_file_name IS NOT NULL;`;
  const env = { ...process.env, PGPASSWORD: config.db.password };

  try {
    const output = execSync(
      `psql -h "${config.db.host}" -p "${config.db.port}" -U "${config.db.user}" -d "${config.db.name}" -t -A -F "|" -c "${query}"`,
      { env, encoding: "utf-8", stdio: ["pipe", "pipe", "pipe"] },
    );

    return output
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0)
      .map((line) => {
        const [storedFileName, uploadStatus] = line.split("|");
        return {
          storedFileName: storedFileName || "",
          uploadStatus: uploadStatus || "UPLOADED",
        };
      });
  } catch (err: unknown) {
    const error = err as Error;
    console.warn(`⚠️ Warning: Could not query database directly (${error.message.trim()}).`);
    return [];
  }
}

/**
 * Fetches object keys from S3 using @aws-sdk/client-s3 with pagination.
 */
export async function fetchS3ObjectKeys(
  config: ReturnType<typeof resolveConfig>,
): Promise<string[]> {
  if (!config.accessKey || !config.secretKey) {
    console.warn(
      "⚠️ S3 credentials not set (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY). S3 listing skipped.",
    );
    return [];
  }

  const s3Client = new S3Client({
    region: config.region,
    endpoint: config.endpoint || undefined,
    credentials: {
      accessKeyId: config.accessKey,
      secretAccessKey: config.secretKey,
    },
    forcePathStyle: true,
  });

  const keys: string[] = [];
  let continuationToken: string | undefined;

  try {
    do {
      const command = new ListObjectsV2Command({
        Bucket: config.bucket,
        Prefix: config.prefix || undefined,
        ContinuationToken: continuationToken,
      });

      const response = await s3Client.send(command);
      if (response.Contents) {
        for (const item of response.Contents) {
          if (item.Key) {
            keys.push(item.Key);
          }
        }
      }
      continuationToken = response.NextContinuationToken;
    } while (continuationToken);

    return keys;
  } catch (err: unknown) {
    const error = err as Error;
    console.warn(`⚠️ Warning: Failed to query S3 bucket via AWS SDK (${error.message.trim()}).`);
    return [];
  }
}

/**
 * Pure comparison logic between database capture records and S3 object keys.
 * Excludes soft-deleted database records (upload_status = 'DELETED').
 */
export function reconcileStorageKeys(
  dbRecords: DbCaptureRecord[] | string[],
  s3ObjectKeys: string[],
  prefix = "captures/",
): {
  activeDbCount: number;
  softDeletedCount: number;
  matchedCount: number;
  missingInS3: string[];
  orphanedInS3: string[];
} {
  // Normalize string array input to DbCaptureRecord objects for backward compatibility
  const normalizedRecords: DbCaptureRecord[] = dbRecords.map((item) => {
    if (typeof item === "string") {
      return { storedFileName: item, uploadStatus: "UPLOADED" };
    }
    return item;
  });

  // Separate active from soft-deleted records
  const activeRecords = normalizedRecords.filter((r) => r.uploadStatus !== "DELETED");
  const softDeletedRecords = normalizedRecords.filter((r) => r.uploadStatus === "DELETED");

  // Build expected S3 keys using backend key-building logic
  const expectedS3Keys = new Set<string>();
  for (const record of activeRecords) {
    if (record.storedFileName) {
      try {
        const s3Key = resolveS3Key(record.storedFileName, prefix);
        expectedS3Keys.add(s3Key);
      } catch (err) {
        console.warn(
          `Invalid storage key skipped: ${record.storedFileName} (${(err as Error).message})`,
        );
      }
    }
  }

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
    // Only consider objects under designated prefix
    if (!prefix || act.startsWith(prefix)) {
      if (!expectedS3Keys.has(act)) {
        orphanedInS3.push(act);
      }
    }
  }

  return {
    activeDbCount: activeRecords.length,
    softDeletedCount: softDeletedRecords.length,
    matchedCount,
    missingInS3,
    orphanedInS3,
  };
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
Task 8.3: Disaster Recovery Capture Storage Integrity Reconciler

Compares PostgreSQL uploaded_files metadata against S3 bucket objects via @aws-sdk/client-s3.
Excludes soft-deleted files (upload_status = 'DELETED').

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
  console.log(`S3 Endpoint:    ${config.endpoint || "(default AWS S3)"}`);
  console.log(`Database Host:  ${config.db.host}:${config.db.port}/${config.db.name}`);
  console.log(`S3 Driver:      @aws-sdk/client-s3 (native S3Client with pagination)`);
  console.log(`------------------------------------------------------------------------------`);

  console.log("Querying database uploaded_files records (excluding soft-deleted)...");
  const dbRecords = fetchDatabaseRecords(config);
  console.log(`Found ${dbRecords.length} total database records.`);

  console.log("Querying S3 object storage keys via @aws-sdk/client-s3...");
  const s3Keys = await fetchS3ObjectKeys(config);
  console.log(`Found ${s3Keys.length} objects in storage bucket.`);

  const comparison = reconcileStorageKeys(dbRecords, s3Keys, config.prefix);

  const durationMs = Date.now() - start;

  console.log(`\n------------------------------------------------------------------------------`);
  console.log(`📊 RECONCILIATION RESULTS`);
  console.log(`------------------------------------------------------------------------------`);
  console.log(`Total Database Records:   ${dbRecords.length}`);
  console.log(`Active Captures (DB):     ${comparison.activeDbCount}`);
  console.log(`Soft-Deleted Ignored:     ${comparison.softDeletedCount}`);
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
    console.log(`\n⚠️ NOTICE: S3 objects present with no corresponding active database record:`);
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
