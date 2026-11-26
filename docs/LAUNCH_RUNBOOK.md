# Velorix Sentinel — Production Launch Runbook

This runbook defines the sequential, time-ordered procedures, GO/NO-GO decision gates, rollback thresholds, and tagging guidance for the controlled launch of Velorix Sentinel.

---

## 1. Staging-First Deployment & Infrastructure Onboarding

> [!IMPORTANT]
> **STAGING FIRST POLICY:** Production onboarding MUST NOT begin until the complete staging deployment has been stood up and verified per [`docs/STAGING_SETUP.md`](STAGING_SETUP.md). All 16 checks across the 6 stages of `bun run staging:verify`, the real disaster recovery drill, and the 48-hour telemetry soak test must pass on staging before deploying to production.

The onboarding and deployment lifecycle proceeds strictly through these sequential stages:

```
[1. Staging Setup per docs/STAGING_SETUP.md]
         │
         ▼
[2. Staging Preflight & Task 8.2 Harness (bun run staging:verify)]
         │
         ▼
[3. Staging DR Drill (backup-db, restore-db, bun run dr:reconcile)]
         │
         ▼
[4. 48-Hour Staging Closed Beta Soak Test]
         │
         ▼
[5. Production Infrastructure Onboarding (Pooler + S3 + Resend + OAuth)]
         │
         ▼
[6. Production Render & Netlify Deploy] ──> [7. Timed Go-Live (T-Minus)]
```

### Step 1: Database Setup — Supabase Session Pooler
1. Log into [Supabase](https://supabase.com) and create project `velorix-sentinel-staging` (and subsequently `velorix-sentinel-prod`) in your target AWS region (e.g., `us-east-1` or `ap-southeast-1`).
2. **Session Pooler Configuration:**
   - **Host:** `aws-0-<region>.pooler.supabase.com`
   - **Port:** `5432` (Session Pooler mode)
   - **Username:** `postgres.<project-ref>`
   - **JDBC URL:** `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`
   - **Password:** Strong alphanumeric password (minimum 32 characters, generated via `docs/PRODUCTION_ENV_CHECKLIST.md`).
   - > [!CAUTION]
     > **NEVER** use the direct database host `db.<project-ref>.supabase.co`. Render has no direct IPv6 routing to Supabase direct hosts, and direct connections do not pool connections, causing connection starvation and timeouts.

### Step 2: Storage Setup — Supabase Storage S3-Compatible API (Default)
1. In Supabase Dashboard > **Storage**, create a private bucket named `velorix-captures-staging` (and `velorix-captures-prod`).
2. Under **Project Settings** > **Storage** > **S3 Access Keys**, generate a new S3 Access Key pair.
3. **Required Environment Variables:**
   - `VELORIX_STORAGE_PROVIDER=s3`
   - `S3_ENDPOINT=https://<project-ref>.supabase.co/storage/v1/s3`
   - `S3_PATH_STYLE_ACCESS=true` *(MANDATORY for Supabase Storage and MinIO)*
   - `AWS_REGION=<matching-supabase-region>` *(MUST match your Supabase project region, e.g. `us-east-1` or `ap-southeast-1`)*
   - `S3_BUCKET_NAME=velorix-captures-staging` (or `velorix-captures-prod`)
   - `AWS_ACCESS_KEY_ID=<supabase-s3-access-key-id>`
   - `AWS_SECRET_ACCESS_KEY=<supabase-s3-secret-key>`
4. **Alternative — AWS S3:**
   - If using native AWS S3 instead of Supabase Storage:
     - `S3_ENDPOINT=` *(leave unset / empty to use standard AWS S3 endpoints)*
     - `S3_PATH_STYLE_ACCESS=false` *(AWS S3 uses virtual-hosted style)*
     - `AWS_REGION=us-east-1` (or your AWS bucket region)
     - `S3_BUCKET_NAME=your-aws-s3-bucket-name`
     - `AWS_ACCESS_KEY_ID=AKIA...` and `AWS_SECRET_ACCESS_KEY=...`

### Step 3: Transactional Email Setup (Resend / SMTP)
1. In [Resend](https://resend.com) (or your chosen SMTP provider), verify your domain (configure DKIM, SPF, and DMARC DNS records).
2. Generate a dedicated API key (`re_...`).
3. Set `SMTP_HOST=smtp.resend.com`, `SMTP_PORT=587`, `SMTP_USERNAME=resend`, `SMTP_PASSWORD=<API_KEY>`, and `MAIL_FROM=notifications@<your-verified-domain>`.
4. Ensure `ALLOW_LOGGING_EMAIL_FALLBACK=false` in staging and production.

### Step 4: OAuth2 Applications
1. **Google Cloud Console:** Create OAuth 2.0 Web Application credentials. Add redirect URI:  
   `https://<RENDER_HOST>/login/oauth2/code/google`
2. **GitHub Developer Settings:** Create OAuth App. Add callback URL:  
   `https://<RENDER_HOST>/login/oauth2/code/github`
3. Record separate Staging and Production Client IDs and Client Secrets.

### Step 5: Render Web Service
1. Create a Web Service connected to your repository with Root Directory `backend`.
2. Configure all variables from [`docs/PRODUCTION_ENV_CHECKLIST.md`](PRODUCTION_ENV_CHECKLIST.md) (or `backend/.env.staging.example` for staging). Ensure `SPRING_PROFILES_ACTIVE=prod` and `BETA_INVITE_ONLY=true`.
3. Set guard overrides `ALLOW_LOGGING_EMAIL_FALLBACK=false` and `ALLOW_FILESYSTEM_STORAGE=false`.

### Step 6: Netlify Frontend
1. Create a Netlify site linked to the repository.
2. In Site Settings > **Build & deploy** > **Environment variables**, set:
   - `VITE_API_BASE_URL=https://<RENDER_HOST>/api`
   - `VITE_FEEDBACK_EMAIL=support@<your-domain>` *(Must be a real email before public launch!)*
3. Trigger a build.

### Step 7: Automated Preflight Drill
Run the preflight health check script against your live endpoints:
- Linux/macOS: `./scripts/staging-preflight.sh https://<RENDER_HOST> https://<NETLIFY_DOMAIN>`
- Windows PowerShell: `.\scripts\staging-preflight.ps1 -BackendUrl https://<RENDER_HOST> -FrontendUrl https://<NETLIFY_DOMAIN>`

### Step 8: Task 8.2 Verification Harness (`bun run staging:verify`)
Execute the automated 16-check, 6-stage test harness against the live deployment:
```bash
# Run all 16 checks across 6 stages sequentially:
bun run staging:verify

# Or target specific stages:
bun run scripts/staging-verify/index.ts --stage auth
bun run scripts/staging-verify/index.ts --stage golden
bun run scripts/staging-verify/index.ts --stage quota
bun run scripts/staging-verify/index.ts --stage abuse
bun run scripts/staging-verify/index.ts --stage large

# For two-phase persistence stage (Check 16: across container restart):
bun run scripts/staging-verify/index.ts --stage persistence --phase before
# [Trigger backend restart / redeploy in Render dashboard]
bun run scripts/staging-verify/index.ts --stage persistence --phase after
```

### Step 9: Real Disaster Recovery Drill
Execute a database backup, schema integrity check, and storage reconciliation per [`RUNBOOK_DISASTER_RECOVERY.md`](../RUNBOOK_DISASTER_RECOVERY.md):
- Non-destructive backup: `.\scripts\dr\backup-db.ps1` or `./scripts/dr/backup-db.sh`
- Schema integrity: `psql "$DATABASE_URL" -f scripts/dr/verify-integrity.sql`
- S3 Storage reconciliation:
  ```bash
  bun run dr:reconcile
  ```
  *(executes `scripts/dr/reconcile-storage.ts` via the `package.json` script `dr:reconcile`)*

### Step 10: 48-Hour Telemetry Soak Test
Monitor telemetry continuously for 48 hours following [`docs/BETA_CHECKLIST.md`](BETA_CHECKLIST.md) §3 and [`docs/MONITORING.md`](MONITORING.md). Verify zero memory leaks, 5xx errors < 0.1%, and consistent HikariCP connection pool health.

---

## 1.1 Docker Setup Note (Local Development & CI)

If running Docker containers or Testcontainers locally on Windows:
1. Ensure **Docker Desktop** is installed and running.
2. Ensure your current Windows user account is a member of the local `docker-users` group:
   ```powershell
   # Run from an Administrator PowerShell prompt:
   net localgroup docker-users "$env:USERNAME" /add
   ```
3. Sign out of Windows and log back in (or restart the Docker Desktop service) for the group membership token to take effect.
4. Verify by running `docker version` in a standard user terminal. Both `Client:` and `Server:` must return valid versions without permission denied errors on the named pipe (`npipe:////./pipe/docker_engine`).

---

## 2. Timed Go-Live Procedure (T-Minus Schedule)

| Timeline | Stage / Action | Responsible | Output / Verification Evidence |
|:---|:---|:---:|:---|
| **T-60m** | **Pre-Launch Code Freeze** | Owner | Merge all approved PRs into `main`. Lock `main` branch to prevent drift. |
| **T-45m** | **Database Snapshot** | Owner | Take a manual Supabase backup and run local dump via `scripts/dr/backup-db.*`. |
| **T-35m** | **Environment Configuration** | Owner | Audit Render environment variables against [`docs/PRODUCTION_ENV_CHECKLIST.md`](PRODUCTION_ENV_CHECKLIST.md). |
| **T-25m** | **Deploy Backend to Render** | Owner | Trigger Manual Deploy > Clear build cache & deploy on Render. |
| **T-20m** | **Verify Backend Startup** | Owner | Check Render logs: Confirm `ProductionProfileValidator` passes with 0 violations and Flyway applies all 12 migrations (V1 through V12). |
| **T-15m** | **Deploy Netlify Frontend** | Owner | Trigger Netlify deploy. Note: `VITE_API_BASE_URL` is baked at build time. Verify build log reflects production API URL. |
| **T-10m** | **DNS & TLS Verification** | Owner | Verify custom domain SSL/TLS certificates on frontend and backend using `curl -Iv https://<domain>`. |
| **T-5m** | **Run Production Preflight** | Owner | Execute `./scripts/staging-preflight.sh` with production URLs. Ensure all probes return green. |
| **T-0** | **Smoke Test with Test Account** | Owner | Register a real account, verify email receipt, log in, upload a benign PCAP, verify analysis completion. |
| **T+10m** | **Open Public Beta Gate** | Owner | In Render, set `BETA_INVITE_ONLY=false` and trigger a zero-downtime redeploy (allowlist is evaluated at startup). |
| **T+20m** | **Public Announcement** | Owner | Publish launch announcement to beta waitlist, social channels, and developer forums. |

---

## 3. Explicit GO / NO-GO Decision Criteria

The launch must be aborted or halted (**NO-GO**) if ANY of the following criteria are triggered:

| Category | GO Condition (Must ALL Pass) | NO-GO Trigger (Halt Immediately) |
|---|---|---|
| **Profile Validation** | `ProductionProfileValidator` logs 0 violations | Any violation logged or `ProductionConfigurationException` thrown |
| **Schema Migration** | All 12 Flyway migrations (V1–V12) apply with checksum match | Flyway migration failure, validation error, or checksum mismatch |
| **Storage Security** | S3 bucket upload and pre-signed read pass; keys user-scoped | S3 access denied, missing credentials, or fallback to filesystem |
| **Email Verification** | Transactional verification email received in inbox within 60s | SMTP connection timeout, authentication rejection, or bounced mail |
| **OAuth2 Logins** | Google & GitHub authentication redirect cleanly with profile | OAuth redirect URI mismatch, CSRF state error, or token failure |
| **Health Probes** | `/actuator/health/readiness` returns HTTP 200 with all components UP | Readiness probe reports `DOWN` or `OUT_OF_SERVICE` > 60s |
| **Error Rate** | 0 unhandled 5xx exceptions during smoke test | Any uncaught internal server error (500) during core user flows |

---

## 4. Rollback Plan & Decision Thresholds

If a critical failure occurs during deployment or within the first 60 minutes of launch, execute the rollback procedure below:

### Decision Thresholds
- **Critical Failure:** 5xx error rate > 5% over 5 minutes.
- **Data Risk:** Ingestion failure or storage permission error preventing user uploads.
- **Auth Failure:** Users unable to log in via password or OAuth2.

### Step-by-Step Rollback Procedure
1. **Frontend Rollback (Netlify):**
   - In Netlify Dashboard > **Deploys**, find the last known good deploy.
   - Click **Publish deploy** to instantly roll back traffic.
2. **Backend Rollback (Render):**
   - In Render Dashboard > Web Service > **Deploys**, select the previous healthy release.
   - Click **Rollback to this deploy**.
3. **Database Compatibility Caveat:**
   - Flyway migrations V1 through V12 are strictly additive. Rolling back backend code to an earlier version is generally safe against additive schemas.
   - If a schema error corrupted data, follow [`RUNBOOK_DISASTER_RECOVERY.md`](../RUNBOOK_DISASTER_RECOVERY.md) (Scenario 1: Database Failure / Corruption) to restore from the pre-launch snapshot.
4. **Mass Session Revocation (if secret compromised):**
   - If `JWT_SECRET` was leaked, update `JWT_SECRET` in Render and execute the revoke query in `RUNBOOK_DISASTER_RECOVERY.md` §3.4.

---

## 5. Release Tagging Guidance

> [!IMPORTANT]
> DO NOT create or push release tags now.  
> Release tags must be created ONLY after the feature PR has been merged into `main` and production verification has succeeded.

Once production deployment and smoke verification are 100% complete, create and push the authoritative release tag from your local terminal:

```bash
# 1. Ensure you are on the updated main branch
git checkout main
git pull origin main

# 2. Verify git log shows the merge commit
git log -n 3 --oneline

# 3. Create annotated signed tag for v0.1.0-beta
git tag -a v0.1.0-beta -m "Release v0.1.0-beta: Controlled Public Beta Launch"

# 4. Push tag to GitHub
git push origin v0.1.0-beta
```
