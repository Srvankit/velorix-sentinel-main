# Velorix Sentinel — Staging Verification & Pre-Flight Testing Guide

**Task 8.2 — Staging Golden PCAP Verification Harness**  
**Harness Location:** `scripts/staging-verify/`  
**Target Environment:** Staging (Render Backend + Netlify Frontend + Supabase Postgres/S3) or Local Pre-flight (`localhost:10000` / `localhost:5173`)

---

## 1. Overview & Architecture

The Staging Verification Harness is an automated TypeScript test suite designed to validate the end-to-end functionality, security controls, rate limiting, and persistence of the Velorix Sentinel platform on a live staging environment prior to production launch.

It exercises **16 checks across 6 functional stages**:
1. **Stage 1: Golden Fixtures (`golden`)** — Checks 1 through 8 (6 golden PCAPs/PCAPNG, duplicate upload deduplication, stuck-run timeout cleanup).
2. **Stage 2: Large File Boundaries (`large`)** — Check 9 (101 MB payload rejection via HTTP 413, 98 MB valid capture upload/parsing, actuator health check).
3. **Stage 3: Quota & Lifecycle (`quota`)** — Check 10 (10-file account limit enforcement, 11th upload rejection via HTTP 409, file deletion, 404 verification, and re-upload).
4. **Stage 4: Authentication & Isolation (`auth`)** — Checks 11 through 14 (unverified email HTTP 403 `EMAIL_NOT_VERIFIED`, refresh token replay family revocation HTTP 401, OAuth dashboard checklist, cross-tenant isolation HTTP 404).
5. **Stage 5: Abuse Prevention & Rate Limiting (`abuse`)** — Check 15 (5 rapid login attempts, 6th HTTP 429 `TOO_MANY_REQUESTS` with `Retry-After` header, cooldown wait, restored login).
6. **Stage 6: Storage & Analysis Persistence (`persistence`)** — Check 16 (two-phase pre/post-restart verification confirming captures and analysis results survive service restarts).

---

## 2. Traceability Matrix

| # | Check Name | Stage | Target Contract / Source Citation | Expected Outcome |
| :---: | :--- | :---: | :--- | :--- |
| **1** | `clean_traffic.pcap` | `golden` | `GoldenPcapFixturesIntegrationTest.testCleanTrafficFixture()` | 0 threats, riskScore 0, UI "Secure" badge, no false positives |
| **2** | `syn_port_scan.pcap` | `golden` | `GoldenPcapFixturesIntegrationTest.testSynPortScanFixture()` | SYN Flood / Port Scan threat detected, elevated riskScore |
| **3** | `dns_tunnel_sample.pcap` | `golden` | `GoldenPcapFixturesIntegrationTest.testDnsTunnelFixture()` | DNS Tunneling threat detected with high entropy payload |
| **4** | `vlan_tagged_traffic.pcap` | `golden` | `GoldenPcapFixturesIntegrationTest.testVlanTaggedTrafficFixture()` | 802.1Q headers stripped transparently, packets extracted |
| **5** | `truncated_sample.pcap` | `golden` | `TruncatedCaptureToleranceTest` + `AnalysisProgress.tsx` | Partial inspection, `truncated: true`, UI truncated banner |
| **6** | `pcapng_sample.pcapng` | `golden` | `PcapngReaderTest` | PCAPNG format auto-detected, packets extracted |
| **7** | Duplicate Upload Deduplication | `golden` | `FileUploadIntegrationTest` | Second upload returns HTTP 409 Conflict with duplicate hash |
| **8** | Stuck Run Timeout Reaper | `golden` | `InspectionQueueRejectionRecoveryIntegrationTest` | Analysis timeout reaper completes/fails zombie runs within PT15M |
| **9** | Large File Boundary (98MB / 101MB) | `large` | `UploadValidationServiceTest` | 101MB returns 413 Payload Too Large; 98MB processes under 60s; Actuator UP |
| **10** | Capture Quota Lifecycle (10 Max) | `quota` | `CaptureQuotaAndVerificationIntegrationTest` | 11th upload returns HTTP 409 Conflict; deletion restores quota |
| **11** | Unverified Email Login Rejection | `auth` | `AccountLifecycleIntegrationTest` | Login returns HTTP 403 Forbidden with `EMAIL_NOT_VERIFIED` |
| **12** | Refresh Token Replay Family Revocation | `auth` | `RefreshTokenReplayIntegrationTest` | Replaying revoked token triggers HTTP 401 & invalidates family |
| **13** | OAuth Flow Integration | `auth` | `OAuthIntegrationTest` | Manual checklist for Google and GitHub OAuth identity links |
| **14** | Cross-Tenant Capture Isolation | `auth` | `CrossTenantIsolationIntegrationTest` | User B requesting User A's file/analysis receives HTTP 404 Not Found |
| **15** | Login Rate Limit & Cooldown | `abuse` | `RateLimitFilterIntegrationTest` | 6th attempt returns HTTP 429 + `Retry-After`; login succeeds after cooldown |
| **16** | Service Restart Persistence | `persistence` | `StorageService` + `AnalysisService` | Two-phase test: capture & findings identical after Render restart |

---

## 3. Prerequisites & Environment Setup

### 3.1 Required Accounts on Staging

To run all automated verification stages, three test accounts should be provisioned:
1. **Primary Verified User (`STAGING_TEST_EMAIL`):**
   - Active, email-verified user for stages `golden`, `large`, `abuse`, and `persistence`.
2. **Secondary Verified User (`STAGING_TEST_EMAIL_2`):**
   - Active, email-verified user used for `quota` (avoids exhausting Primary account's hourly upload quota) and `auth` (Check 14 cross-tenant isolation).
3. **Unverified User (`STAGING_UNVERIFIED_EMAIL`):**
   - Account registered but **not** email-verified, used for Check 11.

> [!TIP]
> If `STAGING_TEST_EMAIL_2` is omitted, the harness uses the primary user for all stages. However, running `golden` (7 uploads) and `quota` (10 uploads) on the same user within 1 hour will exceed the default 10-upload/hour rate limit.

### 3.2 Setting Environment Variables

Create a local `.env.staging` file (never commit secrets) or export variables in your shell:

```bash
# Required
STAGING_API_URL="https://velorix-sentinel-staging.onrender.com"
STAGING_TEST_EMAIL="staging.primary@velorix.net"
STAGING_TEST_PASSWORD="StagingPassword#2026!"

# Recommended for complete isolation
STAGING_FRONTEND_URL="https://staging--velorixsentinel.netlify.app"
STAGING_TEST_EMAIL_2="staging.secondary@velorix.net"
STAGING_TEST_PASSWORD_2="StagingPassword#2026!"
STAGING_UNVERIFIED_EMAIL="staging.unverified@velorix.net"
STAGING_UNVERIFIED_PASSWORD="StagingPassword#2026!"

# Optional overrides
STAGING_TIMEOUT_MS=60000
```

---

## 4. Rate-Limit Budgeting & Plan

The Velorix Sentinel backend enforces multi-tiered Bucket4j rate limits configured in `application.yml`:

| Bucket / Limit | Capacity | Refill Rate | Affected Endpoints |
| :--- | :---: | :---: | :--- |
| **Login IP Capacity** | 5 | 5 tokens / 1 min | `POST /api/v1/auth/login` |
| **Login Email Capacity** | 10 | 10 tokens / 1 min | `POST /api/v1/auth/login` |
| **Register IP Capacity** | 3 | 3 tokens / 1 hour | `POST /api/v1/auth/register` |
| **Forgot Password Capacity** | 3 | 3 tokens / 15 min | `POST /api/v1/auth/forgot-password` |
| **File Upload Capacity** | 10 | 10 tokens / 1 hour | `POST /api/v1/files/upload` |
| **General API Capacity** | 120 | 120 tokens / 1 min | All `/api/v1/**` endpoints |

### Staging Verification Budget Allocation:

1. **Stage `golden` (Checks 1–8):**
   - Uploads: 7 files (6 golden fixtures + 1 duplicate test).
   - Remaining Upload Tokens: 3.
2. **Stage `large` (Check 9):**
   - Uploads: 1 valid 98 MB file (oversized 101 MB file is rejected at multipart filter before token decrement).
   - Remaining Upload Tokens: 2.
3. **Stage `quota` (Check 10):**
   - Uploads: 10 files to hit quota, plus 1 re-upload after deletion = 11 uploads.
   - **Strategy:** If run with the Primary user on standard production limits, this will trigger HTTP 429. The harness automatically uses `STAGING_TEST_EMAIL_2` if supplied.
   - **Alternative for Staging Operator:** In Render Environment Variables, temporarily set `RATE_LIMIT_FILE_UPLOAD_CAPACITY=30` during verification runs.
4. **Stage `abuse` (Check 15):**
   - Deliberately fires 5 rapid logins to exhaust the IP bucket, then asserts HTTP 429 on the 6th attempt.
   - Sleeps for 62 seconds to allow the token bucket to refill, then asserts restored login.

---

## 5. Execution Guide

### 5.1 Run All Stages

```bash
# Using Bun (recommended)
bun run scripts/staging-verify/index.ts

# Or via npm script
npm run staging:verify

# In PowerShell
$env:STAGING_API_URL="https://velorix-sentinel-staging.onrender.com"
$env:STAGING_TEST_EMAIL="staging.primary@velorix.net"
$env:STAGING_TEST_PASSWORD="StagingPassword#2026!"
bun run scripts/staging-verify/index.ts
```

### 5.2 Run Specific Stages

```bash
# Run only Golden PCAP fixtures (Checks 1-8)
bun run scripts/staging-verify/index.ts --stage golden

# Run only Large File Boundary tests (Check 9)
bun run scripts/staging-verify/index.ts --stage large

# Run only Quota & Lifecycle tests (Check 10)
bun run scripts/staging-verify/index.ts --stage quota

# Run only Authentication & Security tests (Checks 11-14)
bun run scripts/staging-verify/index.ts --stage auth

# Run only Rate Limit & Abuse tests (Check 15)
bun run scripts/staging-verify/index.ts --stage abuse
```

### 5.3 Headless / API-Only Run (Skip UI Assertions)

If running in a minimal environment without Chromium/Playwright:
```bash
bun run scripts/staging-verify/index.ts --skip-ui
```

---

## 6. Two-Phase Restart Persistence Guide (Check 16)

Check 16 validates that uploaded captures in Supabase Storage / S3 and analysis records in PostgreSQL survive a full backend service restart.

### Phase 1 (Pre-Restart):
1. Run:
   ```bash
   bun run scripts/staging-verify/index.ts --stage persistence --phase before
   ```
2. The harness uploads `clean_traffic.pcap`, triggers inspection, waits for `COMPLETED`, and saves the identifiers and metrics to `scripts/staging-verify/.persistence-state.json`.

### Manual Render Restart:
1. Log into the **Render Dashboard**.
2. Navigate to your backend web service (`velorix-sentinel-staging`).
3. Click **Manual Deploy** -> **Restart Service** (or trigger a clean restart).
4. Wait until the service status returns to **Live** and `GET /actuator/health` returns `{"status":"UP"}`.

### Phase 2 (Post-Restart):
1. Run:
   ```bash
   bun run scripts/staging-verify/index.ts --stage persistence --phase after
   ```
2. The harness reads `.persistence-state.json`, verifies the capture still exists, calls `POST /api/v1/threats/run/{analysisId}` to re-execute detection, verifies that all threat counts and risk scores match the pre-restart state, and cleans up the test capture.

---

## 7. Manual OAuth Verification Checklist (Check 13)

OAuth providers (Google and GitHub) require an interactive browser redirection and cannot be fully automated without stored credentials. Perform this checklist on the Netlify staging frontend:

### Google OAuth Pre-Flight:
- [ ] 1. Open `https://staging--velorixsentinel.netlify.app/login` in an Incognito browser window.
- [ ] 2. Click **Continue with Google**.
- [ ] 3. Confirm redirection to `accounts.google.com` with client ID matching `GOOGLE_CLIENT_ID`.
- [ ] 4. Sign in with a valid Google account.
- [ ] 5. Confirm consent screen requests profile and email scopes.
- [ ] 6. Confirm redirection back to frontend callback `/auth/callback`.
- [ ] 7. Confirm authorization code exchanged via `POST /api/v1/auth/oauth/google`.
- [ ] 8. Confirm JWT session tokens received and user redirected to `/dashboard`.
- [ ] 9. Check User Profile: avatar and email should match Google account.

### GitHub OAuth Pre-Flight:
- [ ] 1. Open `https://staging--velorixsentinel.netlify.app/login` in an Incognito browser window.
- [ ] 2. Click **Continue with GitHub**.
- [ ] 3. Confirm redirection to `github.com/login/oauth/authorize` with client ID matching `GITHUB_CLIENT_ID`.
- [ ] 4. Authorize Velorix Sentinel.
- [ ] 5. Confirm redirection back to `/auth/callback` and code exchange via `POST /api/v1/auth/oauth/github`.
- [ ] 6. Confirm user profile created in Supabase database with provider `GITHUB`.

---

## 8. Output Reports & Diagnostics

Every execution of the harness generates two files in the output directory (default: `scripts/staging-verify/output/`):
- `results.json`: Machine-readable summary with all check timings, status badges, and metadata.
- `results.md`: GitHub-Flavored Markdown report formatted with stage matrices, pass/fail counts, and reproduction diagnostics.

### Sample Failure Diagnostic in `results.md`:
```markdown
### Check 2: syn_port_scan.pcap
- **Stage:** `golden`
- **Error:** `Threat count mismatch: expected at least 1 threat, got 0`

#### HTTP Request
POST /api/v1/analysis/start/a9f8b7e6-1234-5678-90ab-cdef12345678

#### HTTP Response
HTTP/1.1 202 Accepted
{"status":"QUEUED"}

#### Reproduction Steps
1. Log in with valid credentials via POST /api/v1/auth/login
2. Upload golden capture fixture from backend/src/test/resources/captures/syn_port_scan.pcap
3. Trigger inspection via POST /api/v1/analysis/start/{fileId}
4. Verify expected threat findings and analysis metrics
```

All credentials, tokens, and authorization headers in diagnostics are automatically redacted.

---

## 9. Launch Decision Table

| Staging Outcome | Launch Decision | Action Required |
| :--- | :---: | :--- |
| **All 16 Checks PASS** | 🚀 **PROCEED TO PRODUCTION** | Staging is certified. Schedule Task 8.3 production deployment. |
| **Stage `golden` Check 1–6 FAIL** | 🛑 **BLOCK LAUNCH** | Detection engine or parser regression. Inspect Snort/Pcap parser rules. |
| **Stage `large` Check 9 (413) FAILS** | 🛑 **BLOCK LAUNCH** | Reverse proxy or Spring multipart limit misconfigured. Adjust max size. |
| **Stage `quota` Check 10 FAILS** | 🛑 **BLOCK LAUNCH** | User storage limit check bypassed. Inspect `UploadedFileServiceImpl`. |
| **Stage `auth` Check 11 or 12 FAILS** | 🛑 **BLOCK LAUNCH** | Critical security flaw. Unverified login or token replay vulnerability. |
| **Stage `auth` Check 14 FAILS** | 🛑 **BLOCK LAUNCH** | Critical multi-tenant isolation failure (IDOR). Immediate patch required. |
| **Stage `abuse` Check 15 FAILS** | ⚠️ **CONDITIONAL HOLD** | Bucket4j filter not active. Check Redis or in-memory filter chain order. |
| **Stage `persistence` Check 16 FAILS** | 🛑 **BLOCK LAUNCH** | Ephemeral storage detected. S3 bucket or Supabase credentials invalid. |

