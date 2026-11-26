# Velorix Sentinel — Production Launch Runbook

This runbook defines the sequential, time-ordered procedures, GO/NO-GO decision gates, rollback thresholds, and tagging guidance for the controlled launch of Velorix Sentinel.

---

## 1. First Real Deploy Sequence (End-to-End Onboarding)

Before executing the timed go-live procedure, the infrastructure foundations must be provisioned in the exact sequential order below:

```
[1. Supabase Project] ──> [2. S3 Bucket & Keys] ──> [3. Email Provider Domain] ──> [4. OAuth2 Apps]
         │
         ▼
[5. Render Web Service] ──> [6. Netlify Site] ──> [7. Preflight Drill] ──> [8. Task 8.2 Harness]
         │
         ▼
[9. Real DR Drill] ──> [10. 48-Hour Soak Test] ──> [11. Production Go-Live]
```

### Step 1: Supabase Production Project Creation
1. Log into [Supabase](https://supabase.com) and create project `velorix-sentinel-prod` in your target region (e.g., `us-east-1`).
2. Record the **Project Reference ID**, database host, and port `5432` (Session Pooler).
3. Set a strong password (minimum 32 characters, generated via `PRODUCTION_ENV_CHECKLIST.md`).

### Step 2: S3 Storage Bucket & API Keys
1. In Supabase Dashboard > **Storage**, create a private bucket named `velorix-captures-prod`.
2. Under **Project Settings** > **Storage** > **S3 Access Keys**, generate a new S3 Access Key pair.
3. Record `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and S3 endpoint (`https://<project-ref>.supabase.co/storage/v1/s3`).

### Step 3: Transactional Email Setup (Resend / SMTP)
1. In [Resend](https://resend.com) (or your chosen SMTP provider), verify your production domain (configure DKIM, SPF, and DMARC DNS records).
2. Generate a dedicated production API key (`re_...`).
3. Set `SMTP_HOST=smtp.resend.com`, `SMTP_PORT=587`, `SMTP_USERNAME=resend`, `SMTP_PASSWORD=<API_KEY>`, and `MAIL_FROM=notifications@<your-verified-domain>`.

### Step 4: OAuth2 Production Applications
1. **Google Cloud Console:** Create OAuth 2.0 Web Application credentials. Add redirect URI:  
   `https://<PROD_RENDER_HOST>/login/oauth2/code/google`
2. **GitHub Developer Settings:** Create OAuth App. Add callback URL:  
   `https://<PROD_RENDER_HOST>/login/oauth2/code/github`
3. Record separate production Client IDs and Client Secrets.

### Step 5: Render Production Web Service
1. Create a Web Service connected to your repository with Root Directory `backend`.
2. Configure all variables from [`docs/PRODUCTION_ENV_CHECKLIST.md`](PRODUCTION_ENV_CHECKLIST.md). Ensure `SPRING_PROFILES_ACTIVE=prod` and `BETA_INVITE_ONLY=true`.
3. Set guard overrides `ALLOW_LOGGING_EMAIL_FALLBACK=false` and `ALLOW_FILESYSTEM_STORAGE=false`.

### Step 6: Netlify Production Frontend
1. Create a Netlify site linked to the repository.
2. In Site Settings > **Build & deploy** > **Environment variables**, set:
   - `VITE_API_BASE_URL=https://<PROD_RENDER_HOST>/api`
   - `VITE_FEEDBACK_EMAIL=support@<your-domain>`
3. Trigger a production build.

### Step 7: Automated Preflight Drill
Run the preflight health check script against your live staging/production endpoints:
- Linux/macOS: `./scripts/staging-preflight.sh https://<PROD_RENDER_HOST> https://<PROD_NETLIFY_DOMAIN>`
- Windows: `.\scripts\staging-preflight.ps1 -BackendUrl https://<PROD_RENDER_HOST> -FrontendUrl https://<PROD_NETLIFY_DOMAIN>`

### Step 8: Task 8.2 Verification Harness
Execute the automated test harness against the live deployment in the recommended order:
```bash
bun run scripts/staging-verify/index.ts --stage auth
bun run scripts/staging-verify/index.ts --stage golden
bun run scripts/staging-verify/index.ts --stage quota
bun run scripts/staging-verify/index.ts --stage abuse
bun run scripts/staging-verify/index.ts --stage persistence
bun run scripts/staging-verify/index.ts --stage large
```

### Step 9: Real Disaster Recovery Drill
Execute a non-destructive database backup and storage reconciliation check using `scripts/dr/backup-db.*` and `scripts/dr/reconcile-storage.*` per [`RUNBOOK_DISASTER_RECOVERY.md`](../RUNBOOK_DISASTER_RECOVERY.md).

### Step 10: 48-Hour Telemetry Soak Test
Monitor telemetry continuously for 48 hours following [`docs/BETA_CHECKLIST.md`](BETA_CHECKLIST.md) §3 and [`docs/MONITORING.md`](MONITORING.md). Verify zero memory leaks and consistent HikariCP connection hygiene.

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
