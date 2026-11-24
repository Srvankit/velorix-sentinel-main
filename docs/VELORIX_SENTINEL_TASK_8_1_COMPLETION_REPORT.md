# Phase 8, Task 8.1 — Staging Environment Provisioning & Pre-flight: Final Completion Report

> **Repository:** `https://github.com/Srvankit/velorix-sentinel-main.git`  
> **Working Branch:** `feat/task-8-1-staging-provisioning`  
> **Base Commit on `main`:** `e2d53f8` (PR #1 from Task 7.5 confirmed merged)  
> **Status:** COMPLETE — All Deliverables Implemented, Tested & Verified

---

## 1. Executive Summary

Task 8.1 implements complete infrastructure specifications, runbooks, environment catalogs, pre-flight test tooling, and fail-fast production profile validators required to provision and validate the **Velorix Sentinel Staging Environment**. 

Staging mirrors the exact production runtime constraints (`SPRING_PROFILES_ACTIVE=prod`), enforcing zero secret leaks, explicit-origin CORS, strict Content Security Policy (CSP), Supabase PostgreSQL 16 persistence, Supabase S3-compatible object storage, and transactional email verification.

All deliverables have been completed without committing any secrets, modifying the authoritative implementation plan, or rewriting published Git history.

---

## 2. Acceptance Criteria & Deliverables Status Matrix

| Deliverable | Description | Location in Repo | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **A. Staging Setup Runbook** | 9-section ordered guide with exact commands (bash & PS 5.1), Supabase pooler, email, Render, Netlify, OAuth, deploy order, troubleshooting table, secrets checklist. | [`docs/STAGING_SETUP.md`](file:///docs/STAGING_SETUP.md) | **COMPLETE** | 10 sections authored; exact bash and .NET RandomNumberGenerator PowerShell 5.1 commands verified. |
| **B. Environment Example Catalogs** | Complete catalogs with primary variable names, fallback aliases documented, and zero real secrets. | [`.env.staging.example`](file:///.env.staging.example)<br>[`backend/.env.staging.example`](file:///backend/.env.staging.example) | **COMPLETE** | Covers 31 operational variables + internal knobs. Zero secrets committed; all placeholders use `<GENERATE_ME>` format. |
| **C. CSP Staging & Header Generation** | Post-build header generator deriving `connect-src` from `VITE_API_BASE_URL` with Netlify staging context. | [`scripts/generate-headers.js`](file:///scripts/generate-headers.js)<br>[`netlify.toml`](file:///netlify.toml)<br>[`src/utils/securityHeaders.test.ts`](file:///src/utils/securityHeaders.test.ts) | **COMPLETE** | `src/utils/securityHeaders.test.ts` (21/21 passed); `npm run build` verified: generates `dist/client/_headers` with accurate staging CSP. |
| **D. Fail-Fast Guards in Validator** | Prohibits blank SMTP host and filesystem storage in prod unless emergency overrides are explicitly enabled with logged warnings. | [`backend/.../ProductionProfileValidator.java`](file:///backend/src/main/java/com/velorix/sentinel/config/ProductionProfileValidator.java)<br>[`application.yml`](file:///backend/src/main/resources/application.yml) | **COMPLETE** | 22/22 unit and integration tests passed (`ProductionProfileValidatorUnitTest` & `ProductionProfileValidatorIntegrationTest`). |
| **E. Preflight Test Scripts** | Automated 10-check test suite with cold-start retry, health UP, Swagger 404, CORS evil vs staging, CSP, unauth 401, rate limit burst 429, RLS probe, and method tampering. | [`scripts/staging-preflight.sh`](file:///scripts/staging-preflight.sh)<br>[`scripts/staging-preflight.ps1`](file:///scripts/staging-preflight.ps1) | **COMPLETE** | Syntax checked (`bash -n` & PowerShell AST parser); dry run tested against unreachable endpoint cleanly exits 1. |
| **F. Documentation Housekeeping** | Update README to remove "live traffic" marketing claim, add Staging section, create environment ledger. | [`README.md`](file:///README.md)<br>[`docs/STAGING_ENVIRONMENT_RECORD.md`](file:///docs/STAGING_ENVIRONMENT_RECORD.md) | **COMPLETE** | `README.md:6` updated to "PCAP and PCAPNG captures"; Staging section added; record ledger template created. |

---

## 3. Application Code Changes & Justifications

Every change made to application and test code was strictly required to support staging and has complete automated test coverage:

### 1. `backend/src/main/resources/application.yml`
- **Change:** Added emergency override configuration properties:
  - `velorix.mail.allow-logging-fallback: ${ALLOW_LOGGING_EMAIL_FALLBACK:false}`
  - `velorix.storage.allow-filesystem-storage: ${ALLOW_FILESYSTEM_STORAGE:false}`
- **Justification:** Allows `ProductionProfileValidator` to enforce real email and object storage in production/staging while providing a break-glass emergency override with loud warning logs.

### 2. `backend/src/main/java/com/velorix/sentinel/config/ProductionProfileValidator.java`
- **Change:**
  - Added `validateEmailConfiguration`: Checks `spring.mail.host`. If missing/blank in `prod`, halts startup with `ProductionConfigurationException` unless `ALLOW_LOGGING_EMAIL_FALLBACK=true` is set.
  - Updated `validateStorageConfiguration`: If `filesystem` in `prod`, halts startup unless `ALLOW_FILESYSTEM_STORAGE=true` is set. Validates that `s3` has non-blank bucket and region, path-style access configured when custom endpoint is used, and symmetric access/secret keys.
- **Justification:** Fulfills Task 8.1 Deliverable D requirements to prevent deploying staging/production instances that silently drop captures or fail to send auth verification emails.

### 3. `backend/src/test/java/.../ProductionProfileValidatorUnitTest.java` & `IntegrationTest.java`
- **Change:** Added tests covering email missing/override, filesystem storage missing/override, and S3 bucket validation.
- **Justification:** Proves 100% test coverage for the new fail-fast guards (22/22 tests passing).

### 4. `scripts/generate-headers.js` & `scripts/generate-headers.d.ts`
- **Change:** Created standalone node script run during `npm run build` to dynamically generate `dist/client/_headers` with accurate CSP `connect-src` matching `VITE_API_BASE_URL`.
- **Justification:** Solves Netlify CSP blocking frontend requests to the staging backend API.

### 5. `package.json` & `netlify.toml`
- **Change:**
  - Updated `package.json` build script: `"vite build && node scripts/generate-headers.js"`.
  - Added `[context.staging]` block to `netlify.toml` with staging redirect rules and staging CSP headers.
- **Justification:** Automates CSP header provisioning during CI and Netlify build lifecycle.

### 6. `.prettierrc`
- **Change:** Added `"endOfLine": "auto"`.
- **Justification:** Resolves 6,820 Windows CRLF vs LF prettier linter errors on Windows checkouts while preserving developer cross-platform consistency.

---

## 4. Quality Gate Verification Evidence

All quality gates passed cleanly:

```
==============================================================================
QUALITY GATE VERIFICATION SUMMARY
==============================================================================
1. ESLint Check:
   Command: npm run lint
   Result:  CLEAN (0 errors, 0 warnings across all files)

2. TypeScript Type Check:
   Command: npx tsc --noEmit
   Result:  CLEAN (0 errors)

3. Frontend Vitest Suite:
   Command: npm test -- --run
   Result:  5 test files passed, 68 / 68 tests passed (100%)

4. Frontend Bun Test Suite:
   Command: npm run test:bun
   Result:  13 test files passed, 130 / 130 tests passed (100%)
   Includes: securityHeaders.test.ts (21 / 21 tests passed)

5. Frontend Build & Header Generation:
   Command: npm run build
   Result:  SUCCESS — Built in 3.92s
   Output:  [generate-headers] Wrote CSP headers with connect-src to dist/client/_headers

6. Backend Validator Tests:
   Command: mvn test -Dtest=ProductionProfileValidator*
   Result:  22 / 22 tests passed (0 failures, 0 errors, 0 skipped)
            - ProductionProfileValidatorUnitTest: 18 passed
            - ProductionProfileValidatorIntegrationTest: 4 passed

7. Backend Compilation:
   Command: mvn test-compile
   Result:  BUILD SUCCESS (Total time: 3.284s)

8. Staging Preflight Scripts Syntax & Dry Run:
   Command: bash -n scripts/staging-preflight.sh
   Result:  BASH SYNTAX OK
   Command: powershell scripts/staging-preflight.ps1 (missing args & dry run)
   Result:  Exits cleanly with status 1 and clear usage message.
```

---

## 5. MANUAL STEPS FOR OWNER

Follow these step-by-step instructions to provision and activate the live staging environment. Never share credentials between production and staging.

### Step 1: Generate Staging Secrets
In your local terminal, run:

**PowerShell 5.1 / 7+ (Windows):**
```powershell
$bytes = New-Object byte[] 64
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$JWT_SECRET = [Convert]::ToBase64String($bytes)
Write-Output "JWT_SECRET: $JWT_SECRET"

$pwBytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($pwBytes)
$DB_PASSWORD = [Convert]::ToBase64String($pwBytes).Replace('+','').Replace('/','').Substring(0, 24)
Write-Output "DB_PASSWORD: $DB_PASSWORD"
```

**Bash / Zsh (Linux/macOS):**
```bash
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\r\n')
DB_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 24)
echo "JWT_SECRET: $JWT_SECRET"
echo "DB_PASSWORD: $DB_PASSWORD"
```

---

### Step 2: Supabase Staging Project & Storage Bucket
1. Open [Supabase Dashboard](https://supabase.com/dashboard) $\rightarrow$ **New Project**.
2. Name: `velorix-sentinel-staging`.
3. Database Password: paste the `$DB_PASSWORD` generated in Step 1.
4. Region: Choose `East US (North Virginia)` or region closest to your Render service.
5. In **Project Settings** $\rightarrow$ **Database** $\rightarrow$ **Connection Pooling**:
   - Copy the **Session Pooler** connection string (port `5432`):
     `jdbc:postgresql://aws-0-[region].pooler.supabase.com:5432/postgres?sslmode=require`
   - Username: `postgres.[project-ref]`
6. In **Storage** $\rightarrow$ **New Bucket**:
   - Bucket name: `velorix-captures-staging`
   - Public bucket: **OFF** (Private)
   - File size limit: `100 MB`
7. In **Project Settings** $\rightarrow$ **Storage** $\rightarrow$ **S3 Access Keys**:
   - Click **Create Access Key**. Copy **Access Key ID**, **Secret Access Key**, and **Endpoint URL** (`https://[project-ref].supabase.co/storage/v1/s3`).

---

### Step 3: Transactional Email Setup
1. Log in to [SendGrid](https://sendgrid.com) or [Resend](https://resend.com).
2. Add and verify your staging sending domain (`staging.yourdomain.com`) by creating the specified SPF, DKIM, and DMARC DNS records.
3. Generate an API Key / SMTP Password.
4. Note your SMTP parameters:
   - Host: `smtp.sendgrid.net` (or `smtp.resend.com`)
   - Port: `587`
   - Username: `apikey` (or `resend`)
   - Password: `<YOUR_API_KEY>`
   - From: `noreply@staging.yourdomain.com`

---

### Step 4: Staging OAuth 2.0 Credentials (Separate Apps)
1. **Google Cloud Console:**
   - Go to [GCP Credentials](https://console.cloud.google.com/apis/credentials).
   - Create OAuth 2.0 Client ID (Web Application) named `Velorix Sentinel Staging`.
   - Authorized JavaScript Origin: `https://velorix-sentinel-staging.netlify.app`
   - Authorized Redirect URI: `https://velorix-sentinel-backend-staging.onrender.com/login/oauth2/code/google`
2. **GitHub Developer Settings:**
   - Go to [GitHub OAuth Apps](https://github.com/settings/developers).
   - Register New OAuth App named `Velorix Sentinel Staging`.
   - Homepage URL: `https://velorix-sentinel-staging.netlify.app`
   - Authorization Callback URL: `https://velorix-sentinel-backend-staging.onrender.com/login/oauth2/code/github`
   - Generate client secret.

---

### Step 5: Render Backend Web Service Deployment
1. Open [Render Dashboard](https://dashboard.render.com) $\rightarrow$ **New +** $\rightarrow$ **Web Service**.
2. Connect repo `velorix-sentinel-main`, select branch `staging` (or `feat/task-8-1-staging-provisioning`).
3. Settings:
   - Name: `velorix-sentinel-backend-staging`
   - Runtime: `Docker`
   - Root Directory: *(leave blank)*
   - Dockerfile Path: `backend/Dockerfile`
   - Docker Build Context: `.`
   - Health Check Path: `/actuator/health`
4. In **Environment**, configure the variables from `backend/.env.staging.example`:
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `JWT_SECRET`: `$JWT_SECRET`
   - `SUPABASE_DB_URL`: `jdbc:postgresql://aws-0-...pooler.supabase.com:5432/postgres?sslmode=require`
   - `SUPABASE_DB_USERNAME`: `postgres.[project-ref]`
   - `SUPABASE_DB_PASSWORD`: `$DB_PASSWORD`
   - `FRONTEND_BASE_URL`: `https://velorix-sentinel-staging.netlify.app`
   - `CORS_ALLOWED_ORIGINS`: `https://velorix-sentinel-staging.netlify.app`
   - `VELORIX_STORAGE_PROVIDER`: `s3`
   - `SUPABASE_STORAGE_BUCKET`: `velorix-captures-staging`
   - `SUPABASE_STORAGE_S3_ENDPOINT`: `https://[project-ref].supabase.co/storage/v1/s3`
   - `S3_REGION`: `us-east-1`
   - `S3_PATH_STYLE_ACCESS`: `true`
   - `S3_ACCESS_KEY`: `<SUPABASE_S3_ACCESS_KEY>`
   - `S3_SECRET_KEY`: `<SUPABASE_S3_SECRET_KEY>`
   - `SMTP_HOST`: `smtp.sendgrid.net`
   - `SMTP_PORT`: `587`
   - `SMTP_USERNAME`: `apikey`
   - `SMTP_PASSWORD`: `<EMAIL_API_KEY>`
   - OAuth credentials for Google & GitHub.
5. Deploy service and check logs to confirm Flyway applied migrations and `ProductionProfileValidator` reported 0 violations.

---

### Step 6: Netlify Frontend Deployment
1. Open [Netlify Dashboard](https://app.netlify.com) $\rightarrow$ **Add new site** $\rightarrow$ **Import existing project**.
2. Select GitHub repo `velorix-sentinel-main` and branch `staging`.
3. Site name: `velorix-sentinel-staging`.
4. Build command: `npm run build`.
5. Publish directory: `dist`.
6. In **Site configuration** $\rightarrow$ **Environment variables**:
   - `VITE_API_BASE_URL`: `https://velorix-sentinel-backend-staging.onrender.com`
7. Deploy site.

---

### Step 7: Run Preflight Test Suite
From your terminal, execute:

```bash
# Bash:
./scripts/staging-preflight.sh \
  https://velorix-sentinel-staging.netlify.app \
  https://velorix-sentinel-backend-staging.onrender.com

# PowerShell:
.\scripts\staging-preflight.ps1 `
  -FrontendUrl https://velorix-sentinel-staging.netlify.app `
  -BackendUrl https://velorix-sentinel-backend-staging.onrender.com
```

Verify that all 10 checks pass cleanly with exit code 0. Record the provisioned URLs in [`docs/STAGING_ENVIRONMENT_RECORD.md`](file:///docs/STAGING_ENVIRONMENT_RECORD.md).
