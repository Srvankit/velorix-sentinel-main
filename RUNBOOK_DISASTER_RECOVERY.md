# Velorix Sentinel — Disaster Recovery & Backup Runbook

> **Authoritative Context:** Task 8.3 — Backup, Disaster Recovery & Restore Drill  
> **Repository:** `https://github.com/Srvankit/velorix-sentinel-main.git`  
> **Target RTO (Recovery Time Objective):** `< 30 minutes`  
> **Target RPO (Recovery Point Objective):** `< 24 hours` (or `< 1 minute` with PITR)  
> **Classification:** Internal Engineering & Operational Runbook

---

## 1. Scope, Objectives & Plan-Tier Recovery Matrix

This runbook defines the disaster recovery (DR) procedures for Velorix Sentinel across its cloud infrastructure: **Supabase PostgreSQL 16**, **Supabase S3-Compatible Object Storage**, **Render Web Service (Spring Boot 3.4 / Java 21)**, **Netlify Frontend (TanStack Start SPA)**, and third-party identity/email providers.

### 1.1 Recovery Objectives (RTO & RPO)

- **RTO (< 30 minutes):** Time elapsed between declaring a disaster and restoring end-to-end service availability (`GET /actuator/health` reporting `UP`, user authentication functioning, capture upload and inspection pipelines operational).
- **RPO (< 24 hours baseline, < 60 seconds with PITR):** Maximum tolerable age of recovered data.

### 1.2 Backup Mechanism Capabilities & Supabase Plan Matrix

> [!IMPORTANT]
> **Verify against current Supabase docs & dashboard:** Supabase backup features, retention periods, and Point-in-Time Recovery (PITR) availability depend on your organization's active billing plan and add-ons. Confirm the active project settings under **Project Settings > Database > Backups**.

| Mechanism | Achievable RPO | Achievable RTO | Supabase Plan Required | Mechanism Description | Verification in Dashboard |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **Manual / Scheduled `pg_dump`**<br>*(Default for Free Tier)* | `= Dump Interval`<br>*(e.g., daily, 6h)* | `5–15 min` | **All Plans (Free Tier Default)**<br>*(Free, Pro, Team, Enterprise)* | Automated custom-format dump (`pg_dump -Fc --no-owner --no-privileges`) executed via session pooler port 5432. Mandatory default for Free tier where managed backups are not guaranteed. | Run `scripts/dr/backup-db.sh` or `.ps1` |
| **Daily Automated Backups** | `< 24 hours` | `10–15 min` | **Pro Tier** *(7-day retention)*<br>*(Verify in dashboard)* | Managed daily physical snapshot taken by Supabase. Note: Free tier projects do not offer guaranteed automated backup restore SLAs and pause after 7 days of inactivity. | *Project Settings > Database > Backups > Scheduled Backups* |
| **Point-in-Time Recovery (PITR)** | `< 1 minute` *(down to seconds)* | `10–20 min` | **Pro Tier + PITR Add-on**<br>*(or Team/Enterprise)* | Continuous physical WAL (Write-Ahead Log) archiving enabling restoration to any precise second up to 7–30 days back. *(Verify in dashboard)* | *Project Settings > Database > Backups > Point in Time* |
| **Storage Object Bucket Replication** | `= Sync Interval`<br>*(e.g., 1h, 24h)* | `10–20 min` | **All Plans** | Scheduled cross-bucket or cross-cloud sync (`aws s3 sync`) to secondary bucket or Cloudflare R2 / AWS S3. | Supabase Dashboard > Storage |

---

## 2. Backup Inventory & Ownership Matrix

Not all system components reside in the primary database. The following matrix details where each asset lives, whether it is automatically backed up, and the exact recovery path:

| Asset / Component | Primary Storage Location | Automatically Backed Up? | Recovery Path & Strategy | Responsible Owner |
| :--- | :--- | :---: | :--- | :--- |
| **PostgreSQL Application Data** | Supabase Postgres 16 (`users`, `refresh_tokens`, `analyses`, `threats`, etc.) | **CONDITIONAL** (Pro tier: daily backup/PITR; Free tier: manual `pg_dump` required — verify in dashboard) | Restore via Supabase Dashboard PITR, or restore dump file with `scripts/dr/restore-db.sh` / `.ps1`. | Lead DBA / DevOps |
| **Capture PCAP/PCAPNG Files** | Supabase Storage (`velorix-captures` / S3 bucket) | **NO** (Not part of database backups) | Restore from secondary replica bucket or rerun `reconcile-storage` to identify missing captures. | DevOps / SRE |
| **Backend Configuration & Secrets** | Render Dashboard Environment Variables | **NO** (Render does not version env vars) | Re-apply from authoritative offline inventory (`backend/.env.staging.example` / secrets vault). | Backend Lead |
| **Frontend Configuration & Headers** | Netlify Dashboard & Git (`netlify.toml`) | **YES** (Tracked in Git repository) | Automatic via `git push` or Netlify deployment rollback. | Frontend Lead |
| **OAuth Client Credentials** | Google Cloud Console, GitHub Developer Settings | **NO** | Re-obtain client ID & secret from provider consoles; update Render environment variables. | Security Lead |
| **Transactional Email & DNS** | Resend / SendGrid & DNS Registrar | **NO** | Re-verify domain records (SPF, DKIM, DMARC, MX) in DNS provider; regenerate API keys. | DevOps / SRE |
| **Application Source Code & Migrations** | GitHub (`velorix-sentinel-main`) | **YES** (Distributed Git commit history) | Clone from GitHub or push from local developer mirror. | All Engineers |

---

## 3. Backup Configuration & Best Practices

### 3.1 Supabase Database Backups & PITR
1. **PITR Enablement (Recommended for Production):**
   - Click-path: **Supabase Dashboard** &rarr; Select Project &rarr; **Project Settings** &rarr; **Database** &rarr; **Backups** &rarr; Enable **Point in Time Recovery**.
   - Review retention window (7 days minimum, expandable to 30 days on Enterprise).
2. **Session Pooler Connection for Dumps:**
   - Always connect via the Supabase **Session Pooler** (`aws-0-[region].pooler.supabase.com:5432`), using username `postgres.[project-ref]`.
   - **DO NOT** use the direct host if your network lacks IPv6 routing (Supabase direct host is IPv6-only on AWS).
   - **DO NOT** use the Transaction Pooler (port 6543) for `pg_dump` or `pg_restore` because session-level prepared statements and catalog queries will fail.
3. **Free Tier Backup Clarification (Verify in Dashboard):**
   - The Supabase Free Tier does **not** provide guaranteed automated backup restore SLAs and pauses after 7 days of inactivity.
   - Operators using Free Tier projects must **verify backup availability in the dashboard** (**Project Settings > Database > Backups**) and rely on scheduled manual `pg_dump` via `scripts/dr/backup-db.sh` or `scripts/dr/backup-db.ps1` as the primary and mandatory backup mechanism.

### 3.2 Storage Bucket Protection & Versioning
- **Supabase Storage S3 Versioning Status:** As of current Supabase Storage capabilities, native S3 object versioning is **not** exposed via the Supabase S3 API. *(Verify against current Supabase Storage docs)*.
- **Recommended Protection Strategy:**
   1. Configure a recurring scheduled task (e.g. GitHub Actions or cron worker) running:
      ```bash
      aws s3 sync s3://velorix-captures s3://velorix-captures-backup --endpoint-url "$S3_ENDPOINT"
      ```
   2. Implement an automated cold-archive copy to Cloudflare R2 or AWS S3 Glacier.
   3. Set lifecycle rules preventing accidental bucket deletion.

### 3.3 Manual & Scheduled `pg_dump` Cadence
For environments where managed PITR is not enabled, execute automated dumps according to this schedule:
- **Free Tier (Mandatory Default):** Daily automated dumps or on-demand before any configuration or schema changes (mandatory default because managed backups are not guaranteed).
- **Production (Pro/Team):** Every 6 hours, retaining 30 daily snapshots.
- **Staging:** Daily, retaining 7 days of snapshots.
- **Pre-Migration:** Immediately before applying any major Flyway database migration.

---

## 4. Disaster Recovery Scenario Playbooks

---

### Playbook A: Accidental Table or Row Deletion

* **Detection Signs:** Application logs report foreign key violations or `EntityNotFoundException`; analyst dashboard displays missing records; spike in 404s.
* **Impact:** Loss of specific user accounts, analysis findings, or capture records while database remains online.
* **Expected Recovery Time:** 15–20 minutes.

#### Step-by-Step Recovery:
1. **Isolate Affected Scope:** Identify the deleted table or ID range from audit logs (`activity_logs` table) or user report.
2. **Retrieve Data via PITR or Pre-Incident Dump:**
   - *If PITR is active:* In Supabase Dashboard &rarr; **Database** &rarr; **Backups** &rarr; **PITR**, select a timestamp 2 minutes before the accidental deletion and restore to a **new temporary database project**.
   - *If using `pg_dump` files:* Restore the latest clean dump into an isolated local/staging database using `scripts/dr/restore-db.sh` or `.ps1`.
3. **Extract Missing Rows:**
   ```bash
   pg_dump -h $TEMP_DB_HOST -p 5432 -U $TEMP_DB_USER -d postgres \
     --data-only --table=public.<deleted_table> \
     --file=recovered_rows.sql
   ```
4. **Re-insert into Production:**
   - Review `recovered_rows.sql` for conflicts.
   - Execute:
     ```bash
     psql -h $PROD_DB_HOST -p 5432 -U $PROD_DB_USER -d postgres -f recovered_rows.sql
     ```
5. **Verification:**
   - Query row counts: `SELECT COUNT(*) FROM <deleted_table>;`.
   - Run `scripts/dr/verify-integrity.sql` to confirm zero foreign key orphans.

---

### Playbook B: Full Database Loss or Catastrophic Corruption

* **Detection Signs:** Backend reports `PSQLException: Connection refused` or `FATAL: database "postgres" does not exist`; Supabase project shows degraded/terminated status; `/actuator/health` returns `{"status":"DOWN"}`.
* **Impact:** Complete service outage across all API endpoints.
* **Expected Recovery Time:** 20–30 minutes.

#### Step-by-Step Recovery:
1. **Provision New Supabase Project:**
   - Navigate to **Supabase Dashboard** &rarr; **New Project**.
   - Name: `velorix-sentinel-production` (or `-staging`).
   - Region: Select same cloud region as Render backend (e.g., `Singapore` / `ap-southeast-1` or `us-east-1`) to avoid cross-region network latency.
   - Set a strong, cryptographically generated database password.
2. **Apply Latest Schema & Data Dump:**
   - Using the latest verified dump from `backups/`:
     ```bash
     ./scripts/dr/restore-db.sh --force-remote-restore \
       --host aws-0-<REGION>.pooler.supabase.com \
       --port 5432 \
       --user postgres.<NEW_PROJECT_REF> \
       --dbname postgres \
       --file backups/db_production_latest.dump
     ```
3. **Validate Flyway Schema Migrations:**
   - Confirm all 12 migrations (V1 to V12) are present in `flyway_schema_history`:
     ```sql
     SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
     ```
4. **Re-Verify Row Level Security (RLS) & PostgREST Revocations:**
   - Confirm all 11 public application tables have RLS enabled:
     ```sql
     SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public';
     ```
   - All rows must show `rowsecurity = true`.
5. **Update Render Backend Environment Variables:**
   - Render Dashboard &rarr; Backend Service &rarr; **Environment**:
     - `SUPABASE_DB_URL`: Update with new pooler connection string.
     - `SUPABASE_DB_USERNAME`: `postgres.<NEW_PROJECT_REF>`
     - `SUPABASE_DB_PASSWORD`: `<NEW_PASSWORD>`
   - Trigger **Manual Deploy** &rarr; **Deploy latest commit**.
6. **Verification:**
   - Verify `/actuator/health` returns `{"status":"UP"}` with `db.status: UP`.
   - Run pre-flight script: `./scripts/staging-preflight.sh $FRONTEND_URL $BACKEND_URL`.

---

### Playbook C: Bucket Object Loss (Storage Sync Desynchronization)

* **Detection Signs:** Uploads list successfully, but packet inspection fails with `StorageException: The capture could not be opened`; logs show `S3Exception: The specified key does not exist (Service: S3, Status Code: 404)`.
* **Impact:** Affected captures cannot be processed; existing completed analysis reports remain visible in the UI.
* **Expected Recovery Time:** 15–25 minutes.

#### Application Behavior & Failure Modes:
- When a capture's S3 object is missing during inspection, `InspectionServiceImpl.java` catches `StorageException`, marks the `analyses` record as `FAILED`, and records `failure_reason = "The capture could not be read from storage"`.
- The database record in `uploaded_files` remains intact with `upload_status = 'FAILED'`.
- The frontend displays the analysis card in error state with retry guidance.

#### Step-by-Step Reconciliation:
1. **Run Read-Only Reconciliation Tool:**
   ```bash
   bun run scripts/dr/reconcile-storage.ts
   # Or using PowerShell
   .\scripts\dr\reconcile-storage.ps1
   ```
2. **Inspect Output Categories:**
   - `Missing in S3`: Files in database whose storage object does not exist in bucket.
   - `Orphaned in S3`: S3 objects in bucket with no corresponding database record.
   - `Matched`: Consistent files verified in both database and storage.
3. **Restore Missing Objects:**
   - If secondary backup bucket exists:
     ```bash
     aws s3 sync s3://velorix-captures-backup s3://velorix-captures --endpoint-url "$S3_ENDPOINT"
     ```
   - If unrecoverable: Mark affected database records as `FAILED` so users can re-upload:
     ```sql
     UPDATE uploaded_files SET upload_status = 'FAILED' 
     WHERE stored_file_name IN ('<missing-key-1>', '<missing-key-2>');
     ```
4. **Verification:**
   - Re-run `reconcile-storage` and confirm zero missing records.

---

### Playbook D: Failed or Malformed Flyway Migration

* **Detection Signs:** Backend crashes on startup with `FlywayValidateException`, `Migration checksum mismatch`, or `PSQLException: relation already exists`.
* **Impact:** Backend service enters `CrashLoopBackOff`; deploy fails; API unreachable.
* **Expected Recovery Time:** 10–15 minutes.

#### Critical Architecture Rules:
1. **NO AUTOMATIC ROLLBACK:** SQL migrations in Flyway do not support automatic rollback.
2. **NEVER EDIT APPLIED MIGRATIONS:** Modifying a previously deployed migration (V1–V12) alters its SHA-256 checksum and will permanently halt all backend instances in production!

#### Step-by-Step Resolution:

**Option 1: Forward-Fix Migration (Preferred):**
1. Identify the failing statement from Flyway startup logs.
2. Create a new sequential migration: `V13__fix_<issue_description>.sql`.
3. Commit, test locally with `mvn test-compile`, and deploy via Git.

**Option 2: Safe `flyway_schema_history` Repair (When Startup Is Blocked):**
1. If a migration partially executed and failed midway:
2. Connect to database via `psql`:
   ```sql
   -- 1. Identify the failed migration row (success = false)
   SELECT installed_rank, version, script, success FROM flyway_schema_history WHERE success = false;

   -- 2. Manually undo the DDL statements executed by that script (e.g. DROP TABLE / DROP COLUMN)
   -- 3. Delete the failed migration entry so Flyway can re-attempt it
   DELETE FROM flyway_schema_history WHERE version = '<failed_version>' AND success = false;
   ```
3. Fix the underlying DDL bug in a forward branch and re-deploy.

---

### Playbook E: Bad Backend Application Deploy

* **Detection Signs:** Post-deploy error rate spikes; 502 Bad Gateway responses; health check fails; uncaught exceptions in Render logs.
* **Impact:** Partial or total degradation of backend API.
* **Expected Recovery Time:** `< 5 minutes`.

#### Step-by-Step Rollback via Render Dashboard:
1. Navigate to **Render Dashboard** &rarr; Select Web Service (`velorix-sentinel-backend`).
2. Click the **Events** or **Deploys** tab on the left sidebar.
3. Locate the **previous successful deployment** (marked with a green checkmark).
4. Click the three-dots menu (`...`) on that deploy &rarr; Select **Rollback to this deploy**.
5. Render immediately stops routing traffic to the bad container and spins up the previous working image.

> [!WARNING]
> **Database Compatibility Caveat:** If the bad deploy executed a Flyway migration that dropped or altered columns (`contract` phase), rolling back application code may cause older code to fail if it expects deleted columns. Always follow the **Expand/Contract** migration pattern: add columns first in version N, update code in N+1, remove deprecated columns in N+2.

---

### Playbook F: Compromised JWT Secret (`JWT_SECRET`)

* **Detection Signs:** Security advisory, token forgery detected in authentication logs, leaked environment variable.
* **Impact:** Attacker could forge arbitrary valid JWT access tokens for any user or admin role.
* **Expected Recovery Time:** `< 10 minutes`.

#### Step-by-Step Revocation & Rotation:
1. **Generate New Cryptographic Secret:**
   - Generate at least 64 bytes (512 bits) of cryptographically random Base64:
     ```bash
     # Linux / macOS:
     openssl rand -base64 64 | tr -d '\n'
     
     # Windows PowerShell:
     $bytes = New-Object byte[] 64; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
     ```
2. **Update Render Environment Variable:**
   - Render Dashboard &rarr; Backend Service &rarr; **Environment** &rarr; Update `JWT_SECRET`.
   - Save changes (triggers immediate rolling restart).
   - *Result:* All existing JWT access tokens become instantly invalid because signature verification against the old secret fails immediately in `JwtAuthenticationFilter.java`.
3. **Mass-Revoke All Database Refresh Tokens:**
   - Because refresh tokens are persisted in PostgreSQL, rotating `JWT_SECRET` alone does **not** stop an attacker holding a leaked refresh token from exchanging it for a new access token via `/api/v1/auth/refresh`.
   - Execute the mass-revocation SQL query:
     ```sql
     -- Revoke every active refresh token in the system
     UPDATE refresh_tokens 
     SET revoked = TRUE, 
         revoked_at = NOW(), 
         updated_at = NOW() 
     WHERE revoked = FALSE;
     ```
   - *Schema & Repository Verification:*
     - `revoked`: `BOOLEAN NOT NULL DEFAULT FALSE` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:30`).
     - `revoked_at`: `TIMESTAMPTZ` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:31`).
     - `updated_at`: `TIMESTAMPTZ NOT NULL DEFAULT NOW()` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:35`).
     - Application equivalent: `RefreshTokenRepository.java:36` (`int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now)`).
     - Live execution status: **`UNVERIFIED - needs first real run`** (Docker / PostgreSQL daemon unavailable in local rehearsal environment).
   - *Result:* All user sessions are terminated. Every user must log in again with credentials or OAuth.
4. **Verification:**
   - Attempt to call `/api/v1/auth/refresh` with an old refresh token &rarr; Must return `HTTP 401 Unauthorized`.
   - Log in with valid credentials &rarr; Verifies new tokens work correctly.

---

### Playbook G: Leaked or Abused User Refresh Tokens

* **Detection Signs:** User reports unauthorized account activity, multiple concurrent logins from anomalous geographic locations, or audit log shows suspicious activity.
* **Impact:** Compromise of an individual user account's session.
* **Expected Recovery Time:** `< 5 minutes`.

#### Step-by-Step Targeted Revocation:

**Option 1: Revoke by User ID (Terminates all devices for user):**
```sql
UPDATE refresh_tokens 
SET revoked = TRUE, 
    revoked_at = NOW(), 
    updated_at = NOW() 
WHERE user_id = '<TARGET_USER_UUID>' AND revoked = FALSE;
```
- *Schema & Repository Verification:*
  - `user_id`: `UUID NOT NULL` (defined in `backend/src/main/resources/db/migration/V1__baseline_schema.sql:27`, foreign key to `users(id)`).
  - Application equivalent: `RefreshTokenRepository.java:36` (`revokeAllForUser`).
  - Live execution status: **`UNVERIFIED - needs first real run`**.

**Option 2: Revoke by Token Family (Terminates single compromised device chain):**
```sql
UPDATE refresh_tokens 
SET revoked = TRUE, 
    revoked_at = NOW(), 
    updated_at = NOW() 
WHERE family_id = '<TARGET_FAMILY_UUID>' AND revoked = FALSE;
```
- *Schema & Repository Verification:*
  - `family_id`: `UUID NOT NULL` (added in `backend/src/main/resources/db/migration/V12__refresh_token_family_and_reuse_detection.sql:7,14` with index `idx_refresh_tokens_family_id` at line 16).
  - Application equivalent: `RefreshTokenRepository.java:31`:
    ```java
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.familyId = :familyId AND rt.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
    ```
  - Live execution status: **`UNVERIFIED - needs first real run`**.

*Note on Automatic Protection:* Under `RefreshTokenServiceImpl.java:122`, if an attacker replays an already-revoked refresh token, the backend's automatic reuse detection automatically revokes the entire `family_id` and records an `ActivityType.TOKEN_REVOKED` audit log.

---

### Playbook H: Compromised Third-Party Credentials (S3, SMTP, OAuth)

* **Detection Signs:** AWS/Supabase security alert; unusual spike in S3 egress; spam sent via SMTP account; unauthorized OAuth applications.
* **Impact:** Potential unauthorized storage access, email spoofing, or authentication interruption.
* **Expected Recovery Time:** 10–15 minutes.

#### Rotation Sequence & Downtime Implications:

| Credential | Rotation Location | Render Variables to Update | Service Impact During Rotation |
| :--- | :--- | :--- | :--- |
| **Supabase S3 Access Keys** | Supabase Dashboard &rarr; Storage &rarr; S3 Access Keys | `AWS_ACCESS_KEY_ID`<br>`AWS_SECRET_ACCESS_KEY` | File uploads & capture inspection fail with HTTP 500 until backend restarts with new keys. |
| **SMTP / Email API Key** | SendGrid / Resend / Mailgun Dashboard | `SMTP_PASSWORD` | Registration verification emails & password resets fail with HTTP 500 until updated. |
| **Google OAuth Secret** | Google Cloud Console &rarr; Credentials | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google login returns 401 during rotation. Regular email/password login is unaffected. |
| **GitHub OAuth Secret** | GitHub &rarr; Settings &rarr; Developer settings &rarr; OAuth Apps | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET` | GitHub login returns 401 during rotation. Regular email/password login is unaffected. |

---

### Playbook I: Render Service Loss or Regional Cloud Outage

* **Detection Signs:** Render status page reports major incident; backend URL unreachable; DNS resolution fails.
* **Impact:** Entire backend API unavailable.
* **Expected Recovery Time:** 20–25 minutes.

#### Step-by-Step Migration / Rebuild:
1. **Provision New Web Service:**
   - In Render Dashboard (or alternative provider such as Railway/Fly.io):
   - Connect Git repository: `https://github.com/Srvankit/velorix-sentinel-main.git`.
   - Branch: `main` (or `staging`).
   - Root Directory: `backend`.
   - Runtime: `Docker`.
2. **Apply Environment Variables:**
   - Import all variables from `.env.staging.example` inventory.
   - Ensure region matches the Supabase project region (e.g. `Singapore` or `Oregon`) to maintain low query latency.
3. **Update Netlify Frontend CSP:**
   - If the backend domain changes, update `VITE_API_BASE_URL` in Netlify dashboard.
   - Re-deploy frontend so `scripts/generate-headers.js` builds updated CSP `connect-src` headers.
4. **Verification:**
   - Run preflight script against new host: `./scripts/staging-preflight.sh $FRONTEND_URL $NEW_BACKEND_URL`.

---

## 5. Post-Restore Verification Checklist

After executing any disaster recovery restore, execute the following verification checklist before declaring the system operational:

- [ ] **1. Run Integrity SQL Script:**
  - Execute `scripts/dr/verify-integrity.sql` against the restored database.
  - Confirm:
    - All 11 tables contain expected row counts.
    - `flyway_schema_history` has exactly 12 records with `success = true`.
    - Zero foreign key orphan records exist across all relationships.
    - Zero duplicate `lower(email)` records exist.
    - All 11 public tables report `rowsecurity = true`.
- [ ] **2. Actuator Health Readiness Probe:**
  - `curl -sf https://<BACKEND_HOST>/actuator/health` &rarr; verify `status: "UP"` and `db.status: "UP"`.
- [ ] **3. User Authentication Test:**
  - Execute `POST /api/v1/auth/login` with primary test credentials &rarr; verify JWT access and refresh tokens returned.
- [ ] **4. Capture Upload & Inspection Pipeline Test:**
  - Upload `backend/src/test/resources/captures/clean_traffic.pcap` via `POST /api/v1/files/upload`.
  - Trigger analysis via `POST /api/v1/analysis/start/{fileId}`.
  - Poll `/api/v1/analysis/{id}` until status is `COMPLETED`.
- [ ] **5. Execute Automated Preflight Test Suite:**
  - Run `scripts/staging-preflight.sh` or `scripts/staging-preflight.ps1`.
  - Confirm all 10 preflight checks pass (CORS, CSP, Swagger gating, rate limiting, RLS protection).

---

## 6. RTO and RPO Measurement Method

To measure and record real disaster recovery metrics during drills:

$$\text{RTO} = T_{\text{service\_healthy}} - T_{\text{incident\_declared}}$$
$$\text{RPO} = T_{\text{incident\_declared}} - T_{\text{latest\_recovered\_transaction}}$$

### Measurement Log Table:

| Drill ID | Date | Disaster Scenario | Planned RTO | Measured RTO | Planned RPO | Measured RPO | Outcome | Operator |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `DRILL-01` | *YYYY-MM-DD* | Scenario B (Full DB Restore) | `< 30 min` | *[e.g. 14 min]* | `< 24 h` | *[e.g. 2.5 h]* | `[PASS / FAIL]` | *[Name]* |
| `DRILL-02` | *YYYY-MM-DD* | Scenario F (JWT Compromise) | `< 15 min` | *[e.g. 6 min]* | `0 min` | `0 min` | `[PASS / FAIL]` | *[Name]* |

---

## 7. Escalation Contacts & Post-Incident Review Template

### 7.1 Emergency Incident Contacts

| Role | Name / Title | Contact Channel | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **Incident Commander** | Lead Architect / Tech Lead | Slack / Phone / Pager | Coordinate recovery actions, declare RTO milestones |
| **Lead DBA / Storage** | Database Administrator | Slack / Phone | Database dumps, PITR execution, storage reconciliation |
| **Infrastructure Lead** | DevOps / SRE | Slack / Phone | Render web services, Netlify routing, DNS records |
| **Security Officer** | Security Lead | Slack / Phone | Secret rotation, token revocation, threat forensic audit |

### 7.2 Post-Incident Review (PIR) Template

```markdown
# Post-Incident Review (PIR) — Incident [INC-YYYY-XXXX]

- **Date / Time of Incident:** YYYY-MM-DD HH:MM UTC
- **Duration / Outage Time:** XX minutes
- **Incident Commander:** [Name]
- **Services Affected:** [e.g. PostgreSQL, Storage, Backend API, Web App]

## 1. Summary of Incident
Brief 2-3 sentence overview of what failed and how it was discovered.

## 2. Root Cause Analysis (5 Whys)
1. Why did the service fail? ...
2. Why was that not prevented? ...
3. Why did alerts not fire earlier? ...

## 3. Recovery Timeline
- **HH:MM** — Anomaly detected by monitoring / user report.
- **HH:MM** — Incident declared; Runbook Scenario [X] initiated.
- **HH:MM** — Database restored / Secret rotated / Service redeployed.
- **HH:MM** — Post-restore integrity verification completed.
- **HH:MM** — Incident closed; normal traffic restored.

## 4. Measured Recovery Metrics
- **Measured RTO:** XX minutes (Target: < 30 minutes) — [MET / BREACHED]
- **Measured RPO:** XX minutes/hours (Target: < 24 hours) — [MET / BREACHED]

## 5. Corrective Action Items
| Action Item | Priority | Assignee | Due Date |
| :--- | :---: | :--- | :--- |
| [Action 1] | P1 | [Name] | YYYY-MM-DD |
| [Action 2] | P2 | [Name] | YYYY-MM-DD |
```
