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
| **`restore-db.sh` (Strict Allowlist Refusal Guard)** | Shell execution against 3 non-local targets | **PASS (Refusal Demonstrated on 3/3 targets)** | Ready for staging drill |
| **`restore-db.ps1` (Strict Allowlist Refusal Guard)** | PowerShell execution against 3 non-local targets | **PASS (Refusal Demonstrated on 3/3 targets)** | Ready for staging drill |
| **`restore-db.sh` (Full Restore)** | Live container execution | *Blocked (Docker absent)* | **UNVERIFIED - needs first real run** |
| **`restore-db.ps1` (Full Restore)** | Live container execution | *Blocked (Docker absent)* | **UNVERIFIED - needs first real run** |
| **`verify-integrity.sql`** | SQL syntax audit against V1–V12 migrations | **PASS (Reviewed)** | **UNVERIFIED - needs first real run** |
| **`verify-integrity.sh` / `.ps1`** | Bash & PowerShell parser checks | **PASS (0 syntax errors)** | **UNVERIFIED - needs first real run** |
| **`reconcile-storage.ts`** | Pure comparison logic unit tests (`reconcile.test.ts`) | **PASS (9/9 tests passed)** | **UNVERIFIED on live S3 bucket** |
| **Refresh Token Revocation SQL** | Query validation against V1 & V12 column definitions | **PASS (Reviewed)** | **UNVERIFIED - needs first real run** |

---

## 3. Demonstrated Local Evidence: Strict Allowlist Refusal Guard

Rule 8 and the user instructions require that the restore scripts enforce a strict **ALLOWLIST** (`localhost`, `127.0.0.1`, `::1`, `host.docker.internal`) rather than a keyword denylist. Without `--force-remote-restore` (Bash) or `-ForceRemoteRestore` (PowerShell), any target not in the allowlist must be immediately refused before any network connection, Docker, or `pg_restore` action is attempted.

Refusal was verified against three distinct non-local targets:
1. Supabase pooler host: `aws-0-ap-southeast-1.pooler.supabase.com`
2. Plain IP address: `10.1.2.3`
3. Generic hostname: `db.example.com`

---

### 3.1 PowerShell Refusal Demonstrations (`restore-db.ps1`)

#### Target 1: Supabase Pooler Host (`aws-0-ap-southeast-1.pooler.supabase.com`)
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dr\restore-db.ps1 -HostName "aws-0-ap-southeast-1.pooler.supabase.com" -DumpFile "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'aws-0-ap-southeast-1.pooler.supabase.com' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation switch:
   -ForceRemoteRestore

Example:
   .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName aws-0-ap-southeast-1.pooler.supabase.com -DumpFile <dump>
==============================================================================
```

#### Target 2: Plain IP (`10.1.2.3`)
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dr\restore-db.ps1 -HostName "10.1.2.3" -DumpFile "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: '10.1.2.3' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation switch:
   -ForceRemoteRestore

Example:
   .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName 10.1.2.3 -DumpFile <dump>
==============================================================================
```

#### Target 3: Generic Hostname (`db.example.com`)
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dr\restore-db.ps1 -HostName "db.example.com" -DumpFile "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'db.example.com' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation switch:
   -ForceRemoteRestore

Example:
   .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName db.example.com -DumpFile <dump>
==============================================================================
```

---

### 3.2 Bash Refusal Demonstrations (`restore-db.sh`)

#### Target 1: Supabase Pooler Host (`aws-0-ap-southeast-1.pooler.supabase.com`)
```bash
./scripts/dr/restore-db.sh --host "aws-0-ap-southeast-1.pooler.supabase.com" --file "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
🛑 CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'aws-0-ap-southeast-1.pooler.supabase.com' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation flag:
   --force-remote-restore

Example:
   ./scripts/dr/restore-db.sh --force-remote-restore --host aws-0-ap-southeast-1.pooler.supabase.com --file <dump>
==============================================================================
```

#### Target 2: Plain IP (`10.1.2.3`)
```bash
./scripts/dr/restore-db.sh --host "10.1.2.3" --file "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
🛑 CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: '10.1.2.3' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation flag:
   --force-remote-restore

Example:
   ./scripts/dr/restore-db.sh --force-remote-restore --host 10.1.2.3 --file <dump>
==============================================================================
```

#### Target 3: Generic Hostname (`db.example.com`)
```bash
./scripts/dr/restore-db.sh --host "db.example.com" --file "dummy.dump"
```
**Actual Output (Exit Code 1):**
```text
==============================================================================
🛑 CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED
==============================================================================
Target Host: 'db.example.com' is NOT in the local allowlist!
Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal

Refusing to execute restore. Restoring over any remote, shared, or unverified
database host risks OVERWRITING or DROPPING existing database tables.

If this is a genuine disaster recovery operation against a newly provisioned
recovery database, you MUST pass the explicit confirmation flag:
   --force-remote-restore

Example:
   ./scripts/dr/restore-db.sh --force-remote-restore --host db.example.com --file <dump>
==============================================================================
```

---

## 4. Storage Reconciliation & Unit Test Evidence (`scripts/dr/reconcile-storage.ts`)

### 4.1 S3 Communication & Implementation Details
- **S3 SDK Integration:** `scripts/dr/reconcile-storage.ts` connects to S3-compatible storage using the official `@aws-sdk/client-s3` (`S3Client`, `ListObjectsV2Command`). The package is registered in `package.json` under `devDependencies` and tracked in `package-lock.json`.
- **Pagination Support:** Bucket objects are scanned using `ListObjectsV2Command` with `ContinuationToken` pagination to reliably process buckets containing more than 1,000 objects.
- **Key-Building Logic Parity:** Key building (`resolveS3Key`) strictly mirrors the backend implementation in `backend/src/main/java/com/velorix/sentinel/storage/S3CompatibleFileStorageService.java:195-212` (`resolveObjectKey`):
  1. Replaces Windows backslashes `\` with `/`.
  2. Strips leading slashes (`/`).
  3. Rejects directory traversal attempts (`..`) with an error.
  4. Prepends the configured prefix (default `captures/`).
- **Soft-Delete Exclusion:** In `UploadedFileServiceImpl.java:178`, when a capture is deleted by an analyst, the backend executes `fileStorageService.delete(storageKey)` (removing the physical object from S3) and updates the database row to `upload_status = 'DELETED'`. If a reconciler does not account for this, every soft-deleted file would be falsely reported as an incident ("Missing in S3"). `reconcile-storage.ts` queries `upload_status` and excludes rows where `upload_status === 'DELETED'` from the expected S3 set.

### 4.2 Unit Test Execution (`bun test scripts/dr/__tests__`)
```text
bun test scripts/dr/__tests__
bun test v1.3.10 (30e609e0)

scripts\dr\__tests__\reconcile.test.ts:
(pass) Storage Key Building Logic (S3CompatibleFileStorageService Alignment) > prepends default prefix to clean storage key
(pass) Storage Key Building Logic (S3CompatibleFileStorageService Alignment) > strips leading forward slashes and normalizes backslashes
(pass) Storage Key Building Logic (S3CompatibleFileStorageService Alignment) > supports empty prefix or custom prefix without trailing slash
(pass) Storage Key Building Logic (S3CompatibleFileStorageService Alignment) > rejects path traversal attempts with directory traversal error
(pass) Storage Reconciliation & Soft-Delete Exclusion > accurately matches active DB records against prefixed S3 object keys
(pass) Storage Reconciliation & Soft-Delete Exclusion > EXCLUDES soft-deleted records (upload_status = 'DELETED') from expected S3 objects
(pass) Storage Reconciliation & Soft-Delete Exclusion > identifies active DB captures that are missing from S3 storage
(pass) Storage Reconciliation & Soft-Delete Exclusion > identifies orphaned objects in S3 that have no active DB record
(pass) Storage Reconciliation & Soft-Delete Exclusion > handles custom prefix and backward-compatible string array inputs

 9 pass
 0 fail
 25 expect() calls
Ran 9 tests across 1 file. [351.00ms]
```

---

## 5. Token Revocation SQL Review (Migration V1 & V12 Alignment)

The SQL queries in Runbook Scenario F and G were audited against the actual Flyway schema migrations and repository layer:

1. **Mass Revocation (All Users / Leaked `JWT_SECRET`):**
   ```sql
   UPDATE refresh_tokens 
   SET revoked = TRUE, 
       revoked_at = NOW(), 
       updated_at = NOW() 
   WHERE revoked = FALSE;
   ```
   - `revoked`: `BOOLEAN NOT NULL DEFAULT FALSE` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:30`).
   - `revoked_at`: `TIMESTAMPTZ` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:31`).
   - `updated_at`: `TIMESTAMPTZ NOT NULL DEFAULT NOW()` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:35`).
   - Application equivalent: `RefreshTokenRepository.java:36` (`int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now)`).
   - Live execution: **`UNVERIFIED - needs first real run`** (Docker/PostgreSQL unavailable on local host).

2. **Targeted Revocation by User ID (Compromised User):**
   ```sql
   UPDATE refresh_tokens 
   SET revoked = TRUE, 
       revoked_at = NOW(), 
       updated_at = NOW() 
   WHERE user_id = '<TARGET_USER_UUID>' AND revoked = FALSE;
   ```
   - `user_id`: `UUID NOT NULL` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:27`, foreign key to `users(id)`).
   - Application equivalent: `RefreshTokenRepository.java:36` (`revokeAllForUser`).
   - Live execution: **`UNVERIFIED - needs first real run`**.

3. **Family Revocation (Replay Attack / Leaked Token Chain):**
   ```sql
   UPDATE refresh_tokens 
   SET revoked = TRUE, 
       revoked_at = NOW(), 
       updated_at = NOW() 
   WHERE family_id = '<TARGET_FAMILY_UUID>' AND revoked = FALSE;
   ```
   - `family_id`: `UUID NOT NULL` (added in `backend/src/main/resources/db/migration/V12__refresh_token_family_and_reuse_detection.sql:7,14`).
   - Indexed via `idx_refresh_tokens_family_id` (`backend/src/main/resources/db/migration/V12__refresh_token_family_and_reuse_detection.sql:16`).
   - Application equivalent in `backend/src/main/java/com/velorix/sentinel/repository/RefreshTokenRepository.java:31`:
     ```java
     @Modifying
     @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.familyId = :familyId AND rt.revoked = false")
     int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
     ```
   - Live execution: **`UNVERIFIED - needs first real run`**.

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
