# Velorix Sentinel — Local Disaster Recovery Rehearsal & Verification Report

> **Task Context:** Task 8.3 — Backup, Disaster Recovery & Restore Drill  
> **Host Environment:** Windows 11 Enterprise (Developer Workstation)  
> **Rehearsal Classification:** Local Pre-Flight & Static Verification (NOT a live Supabase production drill)  
> **Status:** STATICALLY VERIFIED / RUNTIME EXECUTION UNVERIFIED (Docker daemon not running on host)

---

## 1. Local Rehearsal Environmental Reality & Docker Status

As required by Rule 8, `docker version` was executed during the rehearsal initialization:

```text
PS C:\Users\AnkitAccess\Downloads\Velorix-Sentinel> docker version
Client:
 Version:           29.7.2
 API version:       1.55
 Go version:        go1.26.5
 Git commit:        a7dcaa6
 Built:             Wed Aug  5 18:31:33 2026
 OS/Arch:           windows/amd64
 Context:           default
failed to connect to the docker API at npipe:////./pipe/docker_engine; check if the path is correct and if the daemon is running: open //./pipe/docker_engine: The system cannot find the file specified.
```

### Direct Assessment:
- **Docker Daemon Availability:** **UNAVAILABLE**. The local Docker Desktop service was not running on the workstation host, preventing the automated spin-up of an isolated `postgres:16` container (`desktop-linux` pipe missing).
- **Host PostgreSQL Daemon:** While PostgreSQL 17 binaries exist on disk at `C:\Program Files\PostgreSQL\17\bin\`, no local database daemon was actively bound or running on port 5432 (`localhost:5432 - no response`).
- **Audit Rule 8 Compliance:** In strict compliance with instructions, **no live database restore is falsely claimed to have passed**. Every script and query that requires a live database connection is explicitly marked as **`UNVERIFIED - needs first real run`** below, accompanied by exact reproduction commands.

---

## 2. Component Verification Status

| Script / Tool / Artifact | Verification Method Applied | Local Result | Staging/Live Status |
| :--- | :--- | :---: | :---: |
| **`backup-db.sh`** | POSIX `bash -n` syntax parse | **PASS (0 syntax errors)** | **UNVERIFIED - needs first real run** |
| **`backup-db.ps1`** | PowerShell AST parser (`ParseFile`) | **PASS (0 syntax errors)** | **UNVERIFIED - needs first real run** |
| **`restore-db.sh` (Refusal Guard)** | Shell execution against remote Supabase host | **PASS (Refusal Demonstrated)** | Ready for staging drill |
| **`restore-db.ps1` (Refusal Guard)** | PowerShell execution against remote Supabase host | **PASS (Refusal Demonstrated)** | Ready for staging drill |
| **`restore-db.sh` (Full Restore)** | Live container execution | *Blocked (Docker absent)* | **UNVERIFIED - needs first real run** |
| **`restore-db.ps1` (Full Restore)** | Live container execution | *Blocked (Docker absent)* | **UNVERIFIED - needs first real run** |
| **`verify-integrity.sql`** | SQL syntax audit against V1–V12 migrations | **PASS (Reviewed)** | **UNVERIFIED - needs first real run** |
| **`verify-integrity.sh` / `.ps1`** | Bash & PowerShell parser checks | **PASS (0 syntax errors)** | **UNVERIFIED - needs first real run** |
| **`reconcile-storage.ts`** | Pure comparison logic unit tests (`reconcile.test.ts`) | **PASS (4/4 tests passed)** | **UNVERIFIED on live S3 bucket** |
| **Refresh Token Revocation SQL** | Query validation against V1 & V12 column definitions | **PASS (Reviewed)** | **UNVERIFIED - needs first real run** |

---

## 3. Demonstrated Local Evidence: Non-Local Target Refusal Guard

Rule 8 requires that the restore script's safety guard—refusing to restore to any remote or cloud database without the explicit override flag—be demonstrable without Docker.

### 3.1 PowerShell Refusal Demonstration
Executed against a simulated Supabase host:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\dr\restore-db.ps1 -HostName "aws-0-ap-southeast-1.pooler.supabase.com" -DumpFile "dummy.dump"
```

**Actual Output:**
```text
==============================================================================
🛑 CRITICAL SAFETY GUARD REFUSAL: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'aws-0-ap-southeast-1.pooler.supabase.com' appears to be a remote, staging, or production database!

Refusing to execute restore because restoring over an active cloud database will
OVERWRITE or DROP existing data tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST re-run with the explicit confirmation switch:
   -ForceRemoteRestore

Example:
   .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName aws-0-ap-southeast-1.pooler.supabase.com -DumpFile <dump>
==============================================================================
```
*Result:* **Exited with code 1 immediately** before any network connections or database operations were attempted.

---

### 3.2 Bash Refusal Demonstration
Executed against a simulated Supabase host via Git Bash:

```bash
./scripts/dr/restore-db.sh --host "aws-0-ap-southeast-1.pooler.supabase.com" --file "dummy.dump"
```

**Actual Output:**
```text
==============================================================================
🛑 CRITICAL SAFETY GUARD REFUSAL: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'aws-0-ap-southeast-1.pooler.supabase.com' appears to be a remote, staging, or production database!

Refusing to execute restore because restoring over an active cloud database will
OVERWRITE or DROP existing data tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST re-run with the explicit confirmation flag:
   --force-remote-restore

Example:
   ./scripts/dr/restore-db.sh --force-remote-restore --host aws-0-ap-southeast-1.pooler.supabase.com --file <dump>
==============================================================================
```
*Result:* **Exited with code 1 immediately**.

---

## 4. Pure Logic Unit Test Evidence (`scripts/dr/__tests__/reconcile.test.ts`)

The storage reconciler comparison algorithm was verified with pure unit tests without network or live S3 dependencies:

```text
bun test scripts/dr/__tests__
bun test v1.3.10 (30e609e0)

scripts\dr\__tests__\reconcile.test.ts:
(pass) Storage Reconciliation Logic > accurately matches DB stored file names against prefixed S3 object keys
(pass) Storage Reconciliation Logic > identifies captures in DB that are missing from S3 storage
(pass) Storage Reconciliation Logic > identifies orphaned objects in S3 that have no DB reference
(pass) Storage Reconciliation Logic > ignores S3 objects outside of designated prefix

 4 pass
 0 fail
 11 expect() calls
Ran 4 tests across 1 file. [181.00ms]
```

---

## 5. Token Revocation SQL Review (Migration V1 & V12 Alignment)

The SQL queries in Runbook Scenario F and G were audited against the actual Flyway schema:

1. **Mass Revocation (All Users / Leaked `JWT_SECRET`):**
   ```sql
   UPDATE refresh_tokens 
   SET revoked = TRUE, 
       revoked_at = NOW(), 
       updated_at = NOW() 
   WHERE revoked = FALSE;
   ```
   - `revoked`: `BOOLEAN NOT NULL DEFAULT FALSE` (defined in `V1__baseline_schema.sql:30`).
   - `revoked_at`: `TIMESTAMPTZ` (defined in `V1__baseline_schema.sql:31`).
   - `updated_at`: `TIMESTAMPTZ NOT NULL DEFAULT NOW()` (defined in `V1__baseline_schema.sql:35`).

2. **Family Revocation (Replay Attack):**
   ```sql
   UPDATE refresh_tokens 
   SET revoked = TRUE, 
       revoked_at = NOW(), 
       updated_at = NOW() 
   WHERE family_id = '<TARGET_FAMILY_UUID>' AND revoked = FALSE;
   ```
   - `family_id`: `UUID NOT NULL` (added in `V12__refresh_token_family_and_reuse_detection.sql:7`).
   - Indexed via `idx_refresh_tokens_family_id` (`V12__refresh_token_family_and_reuse_detection.sql:16`).

---

## 6. Exact Commands for the Owner Once Docker / Database is Available

Once Docker Desktop is started or a staging database is accessible, execute the following commands to complete the live rehearsal:

### Step 1: Start Isolated PostgreSQL 16 Target (Docker)
```bash
docker run -d \
  --name velorix-dr-drill \
  -p 5433:5432 \
  -e POSTGRES_PASSWORD=drillpassword \
  postgres:16
```

### Step 2: Take Live Dump from Staging
```bash
# In Bash:
export DR_DB_HOST="aws-0-<REGION>.pooler.supabase.com"
export DR_DB_PORT=5432
export DR_DB_NAME="postgres"
export DR_DB_USER="postgres.<PROJECT_REF>"
export DR_DB_PASSWORD="<STAGING_DB_PASSWORD>"
./scripts/dr/backup-db.sh

# Or in PowerShell:
$env:DR_DB_HOST = "aws-0-<REGION>.pooler.supabase.com"
$env:DR_DB_PORT = "5432"
$env:DR_DB_NAME = "postgres"
$env:DR_DB_USER = "postgres.<PROJECT_REF>"
$env:DR_DB_PASSWORD = "<STAGING_DB_PASSWORD>"
.\scripts\dr\backup-db.ps1
```

### Step 3: Restore into Isolated Target
```bash
# In Bash:
./scripts/dr/restore-db.sh \
  --host localhost \
  --port 5433 \
  --user postgres \
  --password drillpassword \
  --file backups/velorix_db_latest.dump

# Or in PowerShell:
.\scripts\dr\restore-db.ps1 `
  -HostName "localhost" `
  -Port 5433 `
  -UserName "postgres" `
  -Password "drillpassword" `
  -DumpFile "backups\velorix_db_latest.dump"
```

### Step 4: Run Post-Restore Integrity Verification
```bash
# In Bash:
./scripts/dr/verify-integrity.sh localhost 5433 postgres postgres

# Or in PowerShell:
.\scripts\dr\verify-integrity.ps1 -HostName "localhost" -Port 5433 -UserName "postgres" -Password "drillpassword"
```

### Step 5: Run S3 Storage Reconciliation
```bash
export S3_BUCKET_NAME="velorix-captures-staging"
export S3_ENDPOINT="https://<PROJECT_REF>.supabase.co/storage/v1/s3"
export AWS_ACCESS_KEY_ID="<S3_ACCESS_KEY>"
export AWS_SECRET_ACCESS_KEY="<S3_SECRET_KEY>"
bun run scripts/dr/reconcile-storage.ts
```

### Step 6: Log Results
Fill in the timings and outcomes in [`docs/DR_DRILL_LOG.md`](file:///docs/DR_DRILL_LOG.md).
