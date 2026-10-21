# Velorix Sentinel — Production Readiness Audit

| | |
|---|---|
| **Audit date** | 19 Oct 2026 |
| **Artifact audited** | `Velorix-Sentinel.zip` (698 entries; 205 Java files, 372 TS/TSX files, 6 Flyway migrations) |
| **Method** | Static review of actual source, config, migrations and tests. Cross-checked docs and UI labels against code. |
| **Constraints honoured** | Audit only. No application code modified, no dependencies installed, nothing deployed, no secret values reproduced. |
| **Verdict** | **NOT READY** for public launch (see §10) |

> **Evidence convention.** `path:line` refers to the extracted archive. "Confirmed" = demonstrated by reading the code paths end to end. "Inferred" = follows from the code but was not executed. Anything I could not check is listed in the Appendix.

---

## 0. Executive summary

Velorix Sentinel has a **well-engineered core**: a careful hand-written PCAP/PCAPNG parser, consistent owner-scoped data access, hashed and rotated refresh tokens, hardened uploads, and a clean API envelope. The foundation is better than most pre-launch projects.

It is nonetheless **not launchable**, for reasons that are mostly about *wiring and honesty* rather than code quality:

1. **The headline feature never runs.** Nothing in the backend or frontend calls the threat-detection engine. Every analysis ends with zero threats and the UI renders a green **"Secure"** badge. For a security product this is the worst possible failure mode (silent false negative).
2. **Account recovery and verification cannot work.** The only `EmailService` writes links (including live tokens) to the server log. Login also ignores email verification.
3. **The Supabase plan exposes the schema.** No Row Level Security, grants or policies exist anywhere in the migrations.
4. **Public marketing claims are materially untrue** (encryption at rest with per-tenant keys, Splunk/Sentinel integrations, JA3 extraction, "live traffic", ML ensembles, an AI assistant).
5. **~62% of the UI is shell.** 53 of 85 route files sit behind feature flags hard-coded to `false`, with no backend. There is no AI integration of any kind.
6. **Testing is thin and cannot see the real failure modes.** 18 backend tests run on H2 with Flyway disabled. There are zero frontend tests and no CI. The Docker build skips tests.

None of this is hard to fix, but all of it must be fixed before strangers use the product.

---

## 1. Project architecture and feature inventory

### 1.1 Architecture (as implemented)

| Layer | Implementation | Notes |
|---|---|---|
| Frontend | React 19, TypeScript (strict), TanStack Start/Router, Vite, Tailwind v4, Axios, TanStack Query. Netlify target via `@netlify/vite-plugin-tanstack-start` (`vite.config.ts`). | 85 route files, 56 runtime + 18 dev dependencies. Two lockfiles (`package-lock.json`, `bun.lock`). |
| API | Spring Boot 3.5.0, Java 21, Spring Security (stateless JWT), springdoc. 6 controllers, **33 endpoints**. | Auth 9, Files 4, Analysis 8, Threats 5, Dashboard 6, Health 1. |
| Ingestion | Upload → validate → local-disk store → async inspection on bounded executor (`dpi-` pool, 2 workers, queue 50) → JDBC batch insert of packet rows. | `InspectionServiceImpl`, `InspectionEngine`, `PacketBatchWriter`. |
| Parsing | Custom streaming PCAP/PCAPNG readers + Pcap4J (decode only) for Ethernet/IPv4/IPv6/ARP/TCP/UDP/ICMP. | Header-level; **no payload retained** (only `payload_size`). |
| Detection | 13 rule classes evaluated in one streaming pass over stored packet metadata; risk scorer. | Triggered only by `POST /api/v1/threats/run/{id}` (**no caller**, see C1). |
| Database | PostgreSQL, JPA (`ddl-auto=validate`), Flyway V1–V6, JdbcTemplate bulk writers. | Target: Supabase. `ARCHITECTURE.md` says Neon (docs conflict). |
| Auth | BCrypt(12), JWT access (15 min) + opaque-hash refresh (7 d / 12 h), Google & GitHub via `oauth2Login`. | Role model `ADMIN/ANALYST/USER`; only `USER` is ever assigned. |
| Deployment | Netlify (FE) + Render (Docker BE) + Supabase (DB). | No `render.yaml`, no CI, no IaC. |

### 1.2 Feature inventory: what is real vs. not

| Feature | Status | Evidence |
|---|---|---|
| Register / login / logout / refresh rotation | **Working in code** (untested at runtime) | `AuthServiceImpl.java:100-197`; `RefreshTokenServiceImpl.java:52-110` |
| Email verification / password reset | **Non-functional**: no mail transport | `LoggingEmailService.java:19-31` (sole impl) |
| Google / GitHub OAuth | **Likely broken** (frontend race, proxy headers, config unverifiable) | H3 |
| PCAP / PCAPNG upload, dedupe, delete | **Working in code** | `UploadValidationServiceImpl.java:39-100`; `LocalFileSystemStorageService.java:54-76` |
| Packet decoding & storage | **Working in code**, limits noted (M7, M8) | `PacketDecoder.java:53-229` |
| Network summary, conversations, packet browser | **Working in code** | `AnalysisController.java:115-154` |
| Dashboard summary/charts/activity | **Working in code**, but data is misleading (C1, H1) | `DashboardController.java` |
| **Threat detection** | **Implemented, never invoked** | C1 |
| "Suspicious" packet flag; malicious/clean counters | **Hard-coded false / always 0** | `PacketBatchWriter.java:82`; H1 |
| AI chat / AI summaries / risk prediction / CVE matching | **Do not exist** (UI placeholder stores messages in `localStorage`) | `useCopilotSessions.ts:178`; no LLM dependency in `pom.xml`; no `/api/v1/ai/*` |
| Admin console (13 routes) | UI shell, flag `ADMIN_BACKEND_READY=false`, no backend | `adminService.ts:76` |
| Incidents/cases (5) | UI shell, `INCIDENT_BACKEND_READY=false` | `incidentService.ts:52` |
| Threat intel / IOC / MITRE / map (8) | UI shell, `INTEL_BACKEND_READY=false` | `threatIntelService.ts:46` |
| Observability / ops (14) | UI shell, `OBSERVABILITY_BACKEND_READY=false` | `observabilityService.ts:62` |
| Reports / compliance / board (12) | UI shell, `GOVERNANCE_BACKEND_READY=false` | `governanceService.ts:41` |
| SOC workspace (1) | UI shell, `SOC_BACKEND_READY=false` | `socService.ts:46` |
| History, Notifications | `ModulePlaceholder` only | `history.tsx:29`, `notifications.tsx:26` |
| Settings / Profile | Mostly disabled controls, "Coming soon" | `settings.tsx:229-302` |
| Change password, delete account, session list, MFA | **No backend endpoints** | grep of `backend/src/main` |
| Live traffic / SPAN-TAP sensors / SIEM integrations | **Do not exist** (file upload only) | C-level marketing claims, H6 |

### 1.3 Strengths worth preserving

- **Owner scoping is consistent.** Every read path goes through `CurrentUserProvider.ownerScope()` (`AnalysisServiceImpl.java:81-87`, `ThreatServiceImpl.java:84-88`, `PacketServiceImpl.java:72-75`, `UploadedFileServiceImpl.java:186-196`). Non-owners get 404, not 403. `CoreDomainApiIntegrationTest.hidesDataOwnedByAnotherAccount` covers the main path.
- **JWT hygiene.** Fail-fast on missing/short secret (`JwtTokenProvider.java:117-128`, ≥ 64 bytes), issuer required, `typ` claim enforced so refresh tokens cannot be used as access tokens (`JwtAuthenticationFilter.java:50`). Role is re-read from DB, not trusted from the token.
- **Token storage at rest.** Refresh and verification tokens are stored as SHA-256 hashes, are single-use, and rotated; a password reset revokes all sessions (`AuthServiceImpl.java:219-220`).
- **Upload hardening.** Magic-byte check, opaque UUID storage keys, traversal guard, streamed size cap, atomic temp-file move, checksum dedupe backed by a partial unique index (`V4:17-19`).
- **Parser bounds.** Frame/block length caps, frame-count ceiling (5 M), wall-clock budget, per-frame error containment (`InspectionEngine.java:30-32`).
- **Error handling.** One envelope, no stack traces (`server.error.include-stacktrace: never`), validation errors normalised.
- **Schema discipline.** Flyway owns schema, `validate` mode, CHECK constraints, partial unique index preventing concurrent runs per capture (`V5:66-68`), keyset pagination for streaming (`PacketStreamReader.java:18-34`).
- **No credentials committed.** A pattern scan of the whole archive found only a clearly labelled test-only signing key in `application-test.yml:21`.
- **Mostly honest UI states.** Most unbuilt modules render explicit "Awaiting data" states rather than fabricated numbers.

---

## 2. Findings by severity

### CRITICAL (launch blockers)

#### C1 — Threat detection is never executed; every analysis shows "Secure"
- **Evidence (confirmed).**
  - The only call site of `DetectionService.run` is `ThreatController.java:83-97` (an HTTP endpoint).
  - The inspection pipeline never references detection: `InspectionServiceImpl.execute` (`:119-141`) ends at `runState.complete(...)`.
  - No frontend code calls `/threats/run`: `threatService.ts:6-11` has no such endpoint; the only mutation in the app is `analysisService.start`.
  - `analyses.risk_score` defaults to 0 (`V3:38`). The UI maps score 0 to a **green "Secure"** badge (`risk-band.ts:10`), used in `AnalysisRunList.tsx:65`, `RecentAnalysesTable.tsx:41`, `analysis.tsx:154`, and the overview panel (`AnalysisOverviewPanel.tsx:87`).
- **Impact.** A capture full of port scans and exfiltration is labelled "Secure" and "No threats detected". The product's purpose is silently defeated, and users would act on false reassurance.
- **Fix.** Invoke detection automatically after a successful inspection, asynchronously (see M9). Add an explicit "Not yet scanned" state, never default to "Secure". See P0-1.

#### C2 — No Row Level Security / grants anywhere (Supabase target)
- **Evidence (confirmed in repo; project settings unverified).** `grep` for `ROW LEVEL SECURITY|CREATE POLICY|REVOKE|GRANT` across `V1–V6` returns nothing. All tables are created in `public` (`V1:7,25,44`; `V2:3`; `V3:4-108`; `V6:45`).
- **Impact.** Supabase's Data API (PostgREST) exposes the `public` schema; the project's anon key is public by design. With the Data API enabled, `users` (including password hashes), `refresh_tokens`, `verification_tokens`, `activity_logs`, packets and threats may be readable/writable by anyone. Hash and token-hash disclosure plus write access would be total compromise.
- **Fix.** `ENABLE ROW LEVEL SECURITY` (no permissive policies) on every table, revoke `anon`/`authenticated` privileges, or disable the Data API; keep the backend connecting as the owning role. Add a migration so it is enforced for future tables. See P0-3.

#### C3 — Email delivery is a log statement; reset/verification tokens are written to logs; login ignores verification
- **Evidence (confirmed).**
  - `LoggingEmailService.java:19-31` is the only `EmailService` bean. It logs `"[email] Password reset for {} -> {}"` with the full reset URL at INFO. No mail dependency exists in `pom.xml`.
  - `UserPrincipal.isEnabled()` returns `active` only (`:63-65`); `login` never checks `verified` (`AuthServiceImpl.java:126-146`); the frontend logs the user straight in after registration (`AuthContext.tsx:91-99`).
  - `com.velorix.sentinel` log level defaults to `DEBUG` (`application.yml:132`).
- **Impact.** (a) Real users cannot reset passwords or verify email. (b) Anyone with Render log access can take over any account by requesting a reset and reading the token. (c) Email verification is cosmetic, so anyone can register with an address they do not own.
- **Fix.** Integrate a transactional mail provider; never log URLs/tokens; enforce `verified` on password login; default log level `INFO`. See P0-2.

---

### HIGH

#### H1 — UI reports "CLEAN" packets and "0 malicious / 0 clean frames" regardless of reality
- `PacketBatchWriter.java:82` hard-codes `is_suspicious = false` for every row. `Analysis.maliciousPackets`/`safePackets` (`Analysis.java:129-134`) are never written by any code. The UI renders them as facts: `PacketDetailSheet.tsx:96-97` ("CLEAN"), `AnalysisOverviewPanel.tsx:81-82` ("0 … 0 clean frames"), and a working-looking "suspicious" filter (`PacketTable.tsx:135-137`) that always returns nothing.
- `ARCHITECTURE.md:23` ("nothing is faked in the UI") is therefore inaccurate.
- **Fix.** Either populate these from findings (map threat sample packet numbers → `is_suspicious`) or remove the fields/labels until real.

#### H2 — Pre-account-takeover via OAuth email linking + unverified registration
- `loginWithOAuth` looks up any existing account by email and **force-sets `verified = true`** (`AuthServiceImpl.java:156-172`). Because password registration needs no verification (C3), an attacker can pre-register `victim@example.com` with their own password; when the victim later signs in with Google/GitHub, the account is "verified" and the attacker's password and any refresh tokens they already hold **remain valid**.
- Google's `email_verified` claim is never checked (`OAuth2LoginSuccessHandler.java:34`). GitHub's email fallback does filter on `verified` (`GitHubOAuth2UserService.java:59`).
- **Fix.** Link by provider subject ID, not email; require `email_verified`; on first OAuth link to a pre-existing unverified account, revoke all sessions and invalidate its password.

#### H3 — OAuth login is very likely non-functional end to end
- **Frontend race (confirmed by reading).** `completeOAuthLogin` calls `authService.currentUser()` *before* persisting the tokens (`AuthContext.tsx:134-140`). The Axios request interceptor reads the token from `sessionRef` (`apiClient.ts:62-74`), which is still empty. `/auth/me` goes out unauthenticated, returns 401, refresh has nothing to refresh, `onUnauthorized` clears state, and the callback redirects to `/login` (`oauth.callback.tsx:36-41`).
- **Proxy headers (inferred).** No `server.forward-headers-strategy` / `ForwardedHeaderFilter` anywhere. Behind Render's TLS terminator, Spring will build `http://…/login/oauth2/code/{id}` redirect URIs, which providers reject on mismatch.
- **Config (unverifiable).** No client registrations in the repo; `SecurityConfig.java:75-80` enables `oauth2Login` only if a `ClientRegistrationRepository` exists. If the env vars are missing, "Continue with Google" hits a 404.
- **Hardening.** Access *and refresh* tokens are delivered in the URL fragment (`OAuth2LoginSuccessHandler.java:46-47`); prefer a short-lived one-time code exchanged via POST.

#### H4 — Analysis runs can become permanently stuck, locking the capture out
- `InspectionServiceImpl.dispatch` **swallows** `RejectedExecutionException` (`:196-198`), contradicting the comment at `InspectionExecutorConfig.java:46-48` that says it is rethrown "so the run can be marked failed". The `QUEUED` row stays forever.
- No startup recovery for `QUEUED`/`PROCESSING` rows (no `@PostConstruct`, `@Scheduled`, or lifecycle hook touches runs). A Render restart or redeploy mid-run (the executor is in-memory) leaves the row `PROCESSING` indefinitely.
- `existsActiveForFile` counts both states (`AnalysisRepository.java:52-54`) and `idx_analyses_active_per_file` enforces it in the DB (`V5:66-68`), so the capture can never be re-inspected (HTTP 409 forever).
- **Fix.** Mark the run `FAILED` on rejection; on boot (and periodically) fail runs stuck beyond `maxDuration`; consider a durable queue.

#### H5 — Capture storage is local disk; no quotas
- `LocalFileSystemStorageService` writes to `./data/captures` (`application.yml:73`). Render container filesystems are ephemeral unless a persistent disk is attached, so uploads vanish on redeploy while DB rows stay `READY_FOR_ANALYSIS` (`requireInspectable` then returns "stored capture is missing"). It also prevents running more than one instance.
- No per-user storage or upload-count quota (`UploadedFileServiceImpl.java:69-117`) and no verification requirement, so any registrant can fill the disk 100 MB at a time (and the DB, M10).
- **Fix.** Object storage (Supabase Storage/S3) behind the existing `FileStorageService` seam, plus per-user quotas and retention.

#### H6 — Public marketing/FAQ claims are untrue
Untrue as verified against code (legal and trust exposure for a *security* vendor):

| Claim | Where | Reality |
|---|---|---|
| "Captures are encrypted at rest with per-tenant keys… on-premises" | `Faq.tsx:20` | Plain files on local disk; no encryption, no tenancy model. |
| "Native integrations for Splunk, Microsoft Sentinel, Elastic, Chronicle, PagerDuty, Slack and webhooks, plus a full REST API" | `Faq.tsx:28` | None exist. |
| "Streaming pipeline evaluates live traffic as it arrives" | `Features.tsx:19`; `Hero.tsx:33` | File upload only. |
| "Ensemble models score each flow…" | `Features.tsx:14` | Rule heuristics only; no ML. |
| "Hashes, domains, IPs and JA3 fingerprints" | `Features.tsx:34`; `Faq.tsx:16` | No payload/TLS parsing; `ApplicationProtocolResolver` is port + 3-byte sniff. |
| "AI assistant… grounded, cited answers… no hallucinated verdicts" | `Features.tsx:39`; `AiFeatures.tsx:46` | No AI backend. |
| Behavior baselines, threat-intel feeds, risk prediction, CVE suggestions, SPAN/TAP sensors, SSO/SCIM/data residency, retention tiers | `Features.tsx`, `AiFeatures.tsx`, `Faq.tsx:12,32`, `Pricing.tsx` | Not implemented. |

#### H7 — No rate limiting or abuse controls
No rate limiter, lockout, or CAPTCHA (grep for bucket4j/RateLimit/lockout: none). Exposed: `/auth/login` (BCrypt 12, `SecurityConstants.java:18`, makes each attempt CPU-expensive, so it is also a CPU-DoS vector), `/auth/register`, `/auth/forgot-password` (email bombing once mail exists; each request also **invalidates the victim's previous reset links** via `consumeAllForUser`, `VerificationTokenServiceImpl.java:35`), `/auth/resend-verification`, and `/files/upload` (100 MB).

#### H8 — Tokens in `localStorage` with no CSP or security headers
The full session (access + refresh token) is persisted to `localStorage` (`AuthContext.tsx:34`, `storage.ts:19-26`). Netlify config contains no headers (`netlify.toml` is 9 lines, `public/` has no `_headers`), so there is no CSP, `frame-ancestors`, HSTS, `nosniff`, `Referrer-Policy` or `Permissions-Policy`. Any XSS yields a 7-day refresh token. Current XSS surface is small (markdown has no raw HTML plugin; `chart.tsx:73` is the stock shadcn style injector), but the defence-in-depth is absent.

#### H9 — Test coverage cannot detect the failures above
See §6: 18 backend tests, H2 with Flyway disabled (migrations and PostgreSQL-specific SQL never exercised), 0 frontend tests, 0 CI, Docker build with `-DskipTests` (`Dockerfile:8`).

---

### MEDIUM

| ID | Finding | Evidence |
|---|---|---|
| M1 | **CORS trusts any `*.netlify.app`, `*.lovable.app`, `*.lovableproject.com` origin with credentials, reflected.** Anyone can host `evil.netlify.app`. Bearer tokens limit CSRF impact today, but any future cookie or the OAuth session cookie becomes exposed. Wildcard match is suffix-only and ignores scheme. A production origin is also hard-coded outside env config. | `application.yml:99`; `CorsProperties.java:21-29`; `CorsConfig.java:29`; `ProductionCorsFilter.java:60-69` |
| M2 | **Swagger UI and OpenAPI are public in production**, and the exposed `/api/v1` surface is fully documented. | `ApiConstants.java:32-35`; `application.yml:106-112` |
| M3 | **Refresh rotation lacks reuse detection and is racy.** A replayed (stolen) revoked token merely fails, with no family revocation; `requireUsable` + `revoke` have no locking, so two concurrent refreshes can both succeed. | `AuthServiceImpl.java:177-188`; `RefreshTokenServiceImpl.java:73-104` |
| M4 | **Detection runs synchronously on the HTTP thread inside one `@Transactional`** over up to 5 M packets, holding a pooled connection (pool = 10). It will exceed gateway timeouts and has no concurrency guard, so two simultaneous runs can duplicate findings. Fix together with C1. | `DetectionServiceImpl.java:49-98`; `ThreatDetectionEngine.java:57-105` |
| M5 | **Container/runtime hardening.** Runs as root, no `HEALTHCHECK`, no `.dockerignore`, no JVM memory flags (default heap is ~25% of container RAM), `-DskipTests`. Weak well-known default DB password if env unset. | `Dockerfile:8,20`; `application.yml:10` |
| M6 | **Exception handler gaps.** The catch-all (`GlobalExceptionHandler.java:114`) turns 405/415, a missing multipart `file` part, and invalid `sort` properties into **500s with ERROR logs** (inferred; the handlers for these types are absent). | `GlobalExceptionHandler.java:36-119` |
| M7 | **Truncated captures fail the whole run.** A capture cut mid-record (common when `tcpdump` is killed) throws `EOFException` → `CaptureFormatException` → run `FAILED`, even though earlier packets were already persisted. | `PcapStreamReader.java:93`; `InspectionEngine.java:77-81` |
| M8 | **Decoder coverage gaps (inferred, not executed).** No VLAN (802.1Q), tunnel, or fragment handling: tagged frames fall to the `OTHER` branch with `-` addresses (`PacketDecoder.java:106-139`), silently blinding every rule for those flows. TCP ECE/CWR flags dropped (`:225-226`). No stream reassembly. "Deep" inspection is header-level only. | `PacketDecoder.java` |
| M9 | **Detection accuracy is unvalidated and prone to false positives/negatives.** No test corpus; hard-coded thresholds. Brute-force counts SYNs per (src,dst,port) over the *whole capture* with no time window (30 SYNs flags HIGH; SMB/SMTP/LDAP legitimately exceed this). DNS "tunnelling" fires on payloads > 512 B, which is normal with EDNS0/DNSSEC. Exfiltration needs ≥ 50 MB outbound while uploads are capped at 100 MB, so it can barely trigger. 8 of 13 rules have no test. | `BruteForceRule.java:29-30,81-93`; `DnsAnomalyRule.java:31,97`; `DataExfiltrationRule.java:29`; `DetectionRulesTest.java` |
| M10 | **DB growth/cost with no retention.** Up to 5 M packet rows per run, each carrying 8 secondary indexes plus a unique constraint (`V3:79-83`, `V5:37-39`) → heavy write amplification and storage, which can exhaust a small hosted DB. No scheduled cleanup of expired tokens, activity logs, packets or soft-deleted files (no `@Scheduled`). | `InspectionProperties.java:36`; migrations |
| M11 | **Client IP is spoofable.** `X-Forwarded-For`/`X-Real-IP`/`CF-Connecting-IP` are trusted blindly and the first value is stored in audit and refresh-token rows. Will defeat any IP-based throttling added later. | `RequestUtils.java:11-26` |
| M12 | **No account lifecycle features and no legal pages.** No authenticated change-password, account deletion, data export, session list or MFA endpoints; no privacy policy / terms / cookie routes. Needed for GDPR-style obligations when storing users' network captures. | route list; backend controllers |
| M13 | **Dependency/supply-chain risk (not scanned, see Appendix).** `nitro` pinned to a **beta** (`3.0.260603-beta`); Vite 8 / TanStack Start are young; `@lovable.dev/*` packages bypass the repo's own 24 h release-age guard (`bunfig.toml`); dual lockfiles can drift; Pcap4J 1.8.2 is effectively unmaintained; Spring Boot pinned at 3.5.0 (early patch). | `package.json:87`; `bunfig.toml`; `pom.xml:7-12,27` |
| M14 | **Documentation conflicts with code.** README status table says dashboard/analysis/uploads are "Not started" (`README.md:17`); ARCHITECTURE says Neon vs README Supabase; README says tests cover only auth. | docs |
| M15 | **No observability or backup/DR story.** Actuator exposes only `health,info` (`application.yml:118`); DB health disabled; no metrics, tracing, error tracking or alerting; no documented backup/restore, and no runbook. "Ops" pages in the UI are mocks. | `application.yml:114-127` |

### LOW

| ID | Finding | Evidence |
|---|---|---|
| L1 | Registration reveals whether an email exists (409) while forgot/resend deliberately do not. | `AuthServiceImpl.java:103-105` |
| L2 | Password policy counts chars not bytes (BCrypt uses 72 *bytes*, so multibyte passwords may be truncated or rejected); no common/breached-password check. | `StrongPasswordValidator`, `LoginRequest` |
| L3 | Frontend risk bands (80/60/40/20) claim to "mirror" the backend but differ (backend: critical>0 or ≥80; high>0 or ≥50; ≥25; >0). | `risk-band.ts:1-11` vs `RiskScorer.java:34-48` |
| L4 | Default API URL falls back to the **production** backend when `VITE_API_BASE_URL` is unset, so local/preview builds can hit prod. | `apiConfig.ts:52-66` |
| L5 | Activity log persists attacker-supplied email text on failed sign-in; client `localStorage` copilot chats are not cleared on logout. | `AuthServiceImpl.java:135`; `useCopilotSessions.ts:5` |
| L6 | Dead/unused schema: `detection_rules` table (V6:45-59) has no entity; the `detection.disabled-rules` property is the only toggle. | grep |
| L7 | Committed build/tooling artefacts: `.netlify/v1/functions/server.mjs`, `.lovable/`; legacy storage key `sentinelai.session` (`storage.ts:35`). Netlify `/api/*` redirect is unused (frontend calls the absolute URL). | tree |
| L8 | Sort property accepts any dotted path (`PageRequestParams` regex), enabling expensive `ORDER BY` on large tables and relationship traversal. | `PageRequestParams.java` |
| L9 | IPv6 handling in `NetworkAddresses` is prefix-string based (no `::ffff:` mapped or `2002::` handling). | `NetworkAddresses.java:22-27` |

---

## 3. Authentication and database problems (consolidated)

**Authentication**

| Area | Status | See |
|---|---|---|
| Password hashing (BCrypt 12) | Good | `SecurityConstants.java:18` |
| JWT signing/validation, token typing | Good | `JwtTokenProvider.java`, `JwtAuthenticationFilter.java` |
| Refresh tokens: hashing, rotation, revoke-all on reset | Good | `RefreshTokenServiceImpl` |
| Refresh reuse detection / concurrency | Missing | M3 |
| Logout | Revokes refresh token only; access token valid ≤ 15 min (acceptable, document it) | `AuthServiceImpl.java:190-197` |
| Email verification / password reset | Broken (no mail) and bypassable (not enforced) | C3 |
| Google/GitHub OAuth | Broken frontend, account-linking flaw, proxy config | H2, H3 |
| Brute-force/abuse protection | Absent | H7 |
| Role system | `ANALYST` and `ADMIN` can never be granted through the product; `ProtectedRoute` role gating is client-side only (fine, but no admin APIs exist to protect) | `AuthServiceImpl.java:112` |

**Database**

| Area | Status | See |
|---|---|---|
| Migrations (V1–V6), CHECKs, FKs with cascade, partial unique indexes | Good design | §1.3 |
| Migrations validated against real PostgreSQL in CI | **Never exercised** (tests use H2 + `create-drop`, Flyway off) | H9 |
| RLS / grants | Absent | C2 |
| Integrity of derived columns (`is_suspicious`, `malicious_packets`, `safe_packets`, `risk_score`) | Never populated | C1, H1 |
| Case-insensitive email lookups vs plain unique index on `email` | Works because app lowercases; DB itself is case-sensitive and `findByEmailIgnoreCase` cannot use the index (minor perf; add `lower(email)` unique index) | `V1:22`, `AuthServiceImpl.java:289` |
| Retention / cleanup / index cost | Absent / heavy | M10 |
| Pooling | Hikari 10; Supabase must use the session pooler for JDBC prepared statements | `application.yml:12-18` |
| Backups / restore drills | Not documented | M15 |

---

## 4. Security vulnerabilities (summary map)

| Category (OWASP-style) | Findings |
|---|---|
| Broken access control | Owner scoping solid. **C2** (DB exposure), **H2** (account linking) |
| Authentication failures | **C3, H2, H3, H7, M3** |
| Security misconfiguration | **M1, M2, M5, H8**, default DEBUG logging (**C3**), no forward-headers config (**H3**) |
| Sensitive data exposure | Tokens/URLs in logs (**C3**), tokens in URL fragment and `localStorage` (**H3, H8**), IP spoofing (**M11**) |
| Injection | No SQL injection found: JPA/parameterised JDBC, `UploadedFileSpecifications` parameterised. No raw HTML rendering. (**L8**: sort path) |
| File upload | Well hardened (magic bytes, UUID keys, traversal guard, size cap). Gaps: quotas, durability, verification (**H5**) |
| Denial of service | **H4, H5, H7, M4, M10**; parser limits are good |
| Logging/monitoring | **C3, M15** |
| Software supply chain | **M13** |
| Cryptographic failures | Good primitives. 64-byte HMAC key enforced. One test-only key committed (acceptable, label is clear) |
| SSRF / deserialization | Not applicable (only outbound call is GitHub emails API with a fixed URL) |

---

## 5. Incomplete, mocked or hard-coded functionality

- **Detection never invoked** (C1); **packet/analysis "clean" flags hard-coded** (H1).
- **AI: absent.** No LLM client, no `/api/v1/ai/chat`; the Copilot saves your message to `localStorage` and replies that no model is connected (`useCopilotSessions.ts:178`). Provider tiles (OpenAI, Azure OpenAI, Anthropic, Gemini, local LLM) are decorative (`ProviderPlaceholders.tsx:13-17`). This is honestly labelled in the chat view but contradicted by the landing page (H6).
- **Email transport mocked** (`LoggingEmailService`).
- **53/85 route files** are shells behind permanently-`false` flags: admin 13, ops 14, reports/compliance 12, intel 8, incidents 5, SOC 1; plus 2 assistant routes, `history`, `notifications`, and most of `settings`.
- **Static reference catalogues** (`src/data/*`: MITRE, malware, IOC catalogue, playbooks, compliance frameworks) are fixed content, not live intelligence. Acceptable if labelled as reference material.
- **Rule thresholds** are constants in code; only on/off is configurable (`DETECTION_DISABLED_RULES`).
- **Not hard-coded fake metrics:** I found no fabricated KPI numbers in the dashboard or SOC components; unbuilt modules render empty/"awaiting" states.

---

## 6. Build and test results

| Check | Result |
|---|---|
| `mvn test` / `mvn package` | **NOT RUN.** Maven absent, no local repository cache, network disabled, installs prohibited. |
| `npm run build` / `vite build` / `eslint` / `tsc` | **NOT RUN.** No `node_modules`; cannot install. |
| Java syntax parse (JDK 21 `javac`, parse-only) over 205 main+test files | **0 syntax errors.** (100 unresolved-symbol errors, all from absent third-party jars, as expected.) This is *not* a compile. |
| TypeScript syntactic diagnostics over 372 `.ts/.tsx` files | **0 syntax errors.** (No type-check possible.) |
| `tsconfig` | `strict: true`, bundler resolution; `noUnused*` off. |
| Secrets scan (repo-wide patterns) | **No real secrets.** One labelled test-only key. No `.env` committed (only `.env.example` ×2). |
| Dependency vulnerability scans (`npm audit`, OWASP DC) | **NOT RUN** (no network). |

**Existing tests (static inventory)**

| Class | `@Test` | Covers |
|---|---|---|
| `AuthFlowIntegrationTest` | 3 | register/login/refresh/logout, weak-password rejection, verify + reset |
| `CoreDomainApiIntegrationTest` | 3 | auth required, owned data served, cross-tenant isolation |
| `FileUploadIntegrationTest` | 6 | upload/delete, list/search, rejects bad files, dedupe, isolation |
| `DetectionRulesTest` | 6 | port scan, SYN scan, ICMP flood, brute force, beaconing, risk scoring |
| **Total** | **18** | |

**Gaps.** Tests run on **H2 with `ddl-auto: create-drop` and Flyway disabled** (`application-test.yml:2-12`), so migrations, `lower()`/`bytea` behaviour, partial indexes, `JdbcTemplate` SQL and PostgreSQL types are never exercised. Not covered at all: PCAP/PCAPNG readers, `PacketDecoder`, `InspectionEngine`, run lifecycle (queue rejection, restart), 8 of 13 detection rules (AbnormalPacketSize, DataExfiltration, Ddos, DnsAnomaly, RepeatedConnection, SuspiciousExternalComm, UdpScan, UnknownProtocol), OAuth, JWT unit tests, CORS, rate limits, and the entire frontend (0 test files, no runner configured). There is no CI (`.github/` absent). The Docker image build skips tests (`Dockerfile:8`).

---

## 7. Required environment variables (names only)

**Backend (Render)**

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | **Yes** | Base64, ≥ 64 bytes decoded. App refuses to start without it. |
| `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD` *(or `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)* | **Yes** | Use the **session pooler** JDBC URL with `sslmode=require`. Must be set explicitly because a weak default exists. |
| `FRONTEND_BASE_URL` | **Yes** | Used for email links and OAuth redirect (default is localhost). |
| `CORS_ALLOWED_ORIGINS` | **Yes** | Set to the exact production origin(s); remove wildcards (M1). |
| `PORT` / `SERVER_PORT` | Render injects `PORT` | Docker default 10000. |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` / `_CLIENT_SECRET` / `_SCOPE` | For OAuth | Scope `openid,profile,email`. |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` / `_CLIENT_SECRET` / `_SCOPE` | For OAuth | Scope `user:email`. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | **Add** (`framework`) | Not in `.env.example`; needed for OAuth redirect URIs (H3). |
| Mail provider vars (API key / SMTP host, user, password, from-address) | **Add** | Needed once C3 is fixed. Not present today. |
| Storage vars (bucket, endpoint, key, secret) | **Add** | Needed once H5 is fixed. |
| `JWT_ISSUER`, `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION`, `SHORT_LIVED_REFRESH_EXPIRATION` | Optional | Defaults: `velorix-sentinel`, 15 min, 7 d, 12 h. |
| `EMAIL_VERIFICATION_EXPIRATION`, `PASSWORD_RESET_EXPIRATION` | Optional | Defaults 24 h, 30 min. |
| `JPA_DDL_AUTO` (keep `validate`), `JPA_SHOW_SQL`, `FLYWAY_ENABLED`, `FLYWAY_CONNECT_RETRIES` | Optional | |
| `DB_POOL_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT`, `DB_VALIDATION_TIMEOUT`, `DB_INITIALIZATION_FAIL_TIMEOUT` | Optional | |
| `LOG_LEVEL_APP` | **Set to `INFO`** | Default is `DEBUG` (C3). |
| `LOG_LEVEL_HIBERNATE_SQL`, `LOG_LEVEL_HIBERNATE_BIND` | Optional | Keep `INFO`. |
| `CAPTURE_STORAGE_LOCATION`, `CAPTURE_MAX_FILE_SIZE`, `MAX_UPLOAD_FILE_SIZE`, `MAX_UPLOAD_REQUEST_SIZE` | Optional | Replace local storage (H5). |
| `DETECTION_ENGINE_VERSION`, `DETECTION_BATCH_SIZE`, `DETECTION_STREAM_CHUNK_SIZE`, `DETECTION_MAX_FINDINGS_PER_RULE`, `DETECTION_MAX_SAMPLE_FRAMES`, `DETECTION_MAX_TRACKED_KEYS`, `DETECTION_DISABLED_RULES` | Optional | Missing from `.env.example`. |
| `VELORIX_INSPECTION_*` (concurrency, queue-capacity, max-packets, max-duration, batch-size) | Optional | Bound from `velorix.inspection.*` but **absent from `application.yml`**, so code defaults apply (2 workers, queue 50, 5 M packets, 15 min). Size for the chosen instance. |

**Frontend (Netlify)**

| Variable | Required | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | **Yes** | Must include `/api`. Falls back to the production URL if unset (L4). |

---

## 8. Prioritized fixes, dependencies and acceptance criteria

### P0: must be complete before any external user

| # | Fix | Depends on | Acceptance criteria |
|---|---|---|---|
| P0-1 | **Wire detection into the pipeline** (C1, H1, M4): run detection asynchronously after `complete`, in its own transaction, with a per-analysis guard; populate `is_suspicious`/`malicious_packets`/`safe_packets` from findings (or remove them); add a "Not scanned / Scanning" state and stop defaulting score 0 to "Secure". | None | Uploading a known port-scan PCAP yields ≥ 1 threat and `status ≠ SECURE` with no manual API call. A clean PCAP shows "Secure" only after detection has completed. A run that has not been scanned never shows green. Two concurrent triggers produce no duplicate findings. |
| P0-2 | **Auth recovery & identity** (C3, H2, H3, H7): real mail provider; never log tokens; enforce verified email for password login; link OAuth by provider subject, require `email_verified`, revoke sessions on first link; fix the OAuth callback ordering; one-time-code exchange instead of tokens in the URL; add `forward-headers-strategy`; rate-limit login/register/forgot/resend (per IP *and* per account) with a trusted-proxy-aware IP. | Mail provider account; OAuth apps configured with the exact Render redirect URIs | A new user receives a verification email and cannot log in until verified. Password reset works end to end. Logs contain no token or link at any level. Pre-registered-account takeover test fails. Google and GitHub sign-in succeed on staging. > N failed logins from one IP/account return 429. |
| P0-3 | **Lock down Supabase** (C2): RLS on every table, revoke `anon`/`authenticated`, or disable the Data API; add a migration + guard test. | Supabase project | With the anon key, `GET /rest/v1/users` (and every other table) returns 401/empty. Backend still operates as the owning role. |
| P0-4 | **Run lifecycle & durable storage** (H4, H5, M7): fail rejected runs; recover stuck runs on boot and periodically; object storage behind `FileStorageService`; per-user quota; tolerate truncated captures (mark `truncated`, keep decoded frames). | Storage bucket/credentials | Killing the service mid-run and restarting leaves the run `FAILED` (or resumed) and the capture re-inspectable. Redeploying keeps uploaded files. Exceeding quota returns 4xx. A tail-truncated PCAP completes as "truncated". |
| P0-5 | **Make the public surface truthful** (H6, §5): remove or correct every claim in §H6; hide or clearly flag the 53 unbuilt routes and the AI assistant (feature-flag out of navigation); add privacy policy and terms. | Product/legal review | A claims-vs-capabilities checklist signed off. No nav item leads to a shell. No marketing statement lacks a corresponding shipped feature. |

### P1: before public beta (can follow a closed beta)

| # | Fix | Depends on | Acceptance criteria |
|---|---|---|---|
| P1-1 | **Test foundation** (H9): Testcontainers PostgreSQL with Flyway on in all integration tests; unit tests for readers/decoder/engine with golden PCAPs; tests for all 13 rules incl. false-positive fixtures; OAuth/JWT/CORS tests; frontend Vitest + a Playwright smoke (register → upload → analyse → threats). | None | CI fails on any failing test; migrations applied in tests; ≥ 80% coverage on `detection`, `inspection`, `security` packages. |
| P1-2 | **CI/CD**: GitHub Actions for build, test, lint, `npm audit`/OWASP Dependency-Check; Docker build without `-DskipTests`; branch protection; pick one lockfile. | P1-1 | A PR cannot merge with failing tests or high-severity advisories. |
| P1-3 | **CORS & headers** (M1, M2, H8): exact-origin allow-list from env, scheme-aware; Netlify `_headers` with CSP, HSTS, `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; disable Swagger in production. Consider moving the refresh token to an `HttpOnly; Secure; SameSite` cookie. | P0-2 | Securityheaders.com grade ≥ A; `Origin: https://evil.netlify.app` receives no ACAO; `/swagger-ui.html` returns 404 in prod. |
| P1-4 | **Token hardening** (M3): reuse detection with family revocation; row lock on rotate. | None | Replaying a rotated token revokes the entire session family; concurrent refresh yields exactly one success. |
| P1-5 | **Detection quality** (M8, M9): time-windowed brute-force; DNS rule using EDNS-aware thresholds; reconsider exfil threshold; VLAN/ tunnel decoding; make thresholds configurable; document each rule's limits in-product. | P0-1, P1-1 | Precision/recall measured on a labelled corpus and recorded; documented false-positive rate per rule. |
| P1-6 | **Observability & ops** (M15, M5): structured logs, error tracking, metrics/Actuator Prometheus behind auth, uptime check, alerts on stuck runs and 5xx; non-root container, healthcheck, JVM `-XX:MaxRAMPercentage`; `render.yaml`. | P0-4 | Alert fires on a deliberately stuck run; dashboards show request rate/latency/error; image runs as non-root. |
| P1-7 | **Data lifecycle** (M10, M12): retention jobs (tokens, logs, soft-deleted files, packets), index review on `packets`, delete-account and data-export endpoints, change-password. | P0-4 | Expired tokens purged nightly; account deletion removes files and rows; retention documented. |
| P1-8 | **Backups & DR** (M15): enable PITR/backups, document and rehearse restore, back up object storage. | P0-3, P0-4 | Restore drill completed with recorded RTO/RPO. |

### P2: hardening and polish

Fix documentation drift (M14); unify Neon/Supabase wording; reconcile risk-band thresholds (L3); remove committed tooling artefacts (L7); registration enumeration (L1); password policy bytes/breach check (L2); drop or use `detection_rules` (L6); default `VITE_API_BASE_URL` to required (L4); case-insensitive email unique index (§3); restrict sort properties (L8); dependency freshness review and pin beta `nitro` (M13).

---

## 9. Recommended deployment sequence

1. **Fix P0-1 … P0-5 in a branch**; keep production *undeployed to the public* until they pass.
2. **Provision Supabase** (staging first). Apply RLS/grants migration *before* any app data exists (P0-3). Verify with the anon key.
3. **Create object-storage bucket** and mail-provider domain (SPF/DKIM/DMARC) (P0-2, P0-4).
4. **Deploy backend to Render staging**: set all env vars (§7, `LOG_LEVEL_APP=INFO`, forward-headers strategy, exact CORS origin); run Flyway against staging DB; confirm `/actuator/health`. Size the instance for memory and set JVM flags.
5. **Register OAuth apps** (Google, GitHub) with the exact staging then production redirect URIs `https://<render-host>/login/oauth2/code/{google|github}`.
6. **Deploy frontend to Netlify staging** with `VITE_API_BASE_URL` set explicitly, security headers applied.
7. **End-to-end smoke on staging** with a golden PCAP set (clean, port scan, SYN scan, DNS tunnel, truncated, pcapng, VLAN-tagged, 100 MB): register → verify email → upload → inspect → detect → view threats → delete account.
8. **Run CI gates** (tests on real PostgreSQL, dependency scan) and a short external or self-run security pass (auth, CORS, RLS, upload abuse, rate limits).
9. **Closed beta** (invite-only allowlist, quotas on, monitoring and alerts live, backup restore rehearsed).
10. **Promote to production** (repeat steps 2-6 with production resources), then open **public beta** with the narrowed, truthful scope: *PCAP upload → decode → rule-based detection → dashboard*.

---

## 10. Final verdict

# ❌ NOT READY

**Why.** The one capability that defines the product, detecting threats, is never triggered, and the UI turns that silence into a green "Secure". Combined with non-functional account recovery, an unprotected Supabase schema, broken OAuth, stuck-run and storage-durability failure modes, and public claims the code does not support, launching now would mislead users about their security posture and expose their data.

**Path to "Ready for Public Beta".** Complete P0-1 through P0-5. At that point the product is a credible, honestly scoped *PCAP analysis beta* (upload → decode → rule-based findings → dashboard) and would qualify as **Conditionally Ready** pending the P1 items (tests/CI, headers/CORS, token hardening, observability, backups). It should move to **Ready for Public Beta** once P1-1 through P1-8 are complete and the staging smoke suite passes.

---

## Five most important issues to fix first

1. **Detection never runs, and "no scan" is shown as "Secure" / "CLEAN"** (C1, H1). Wire detection asynchronously after inspection; add a "not scanned" state; populate or remove the always-false flags.
2. **Account security is broken end to end** (C3, H2, H3, H7). Real email delivery with no tokens in logs, enforce verification, fix OAuth account linking and the callback bug, add rate limiting.
3. **Supabase schema is unprotected** (C2). Enable RLS / revoke API roles / disable the Data API, before any data is loaded.
4. **Runs and uploads are not durable** (H4, H5). Fail rejected runs, recover stuck ones, move captures to object storage, and add quotas.
5. **Public claims and unbuilt modules misrepresent the product** (H6, §5). Remove false claims (encryption at rest, integrations, live traffic, ML, AI assistant) and hide the 53 shell routes until real.

---

## Appendix: what I could not verify

- **Runtime behaviour of anything**: no build, test run, browser session, or deployed environment was available. All "Confirmed" items are code-path proofs, not executed reproductions.
- **Whether the Supabase project has the Data API enabled**, and whether any RLS was applied manually outside the migrations (C2).
- **Whether Render has OAuth client variables, a persistent disk, or the intended `CORS_ALLOWED_ORIGINS`** set (H3, H5, M1).
- **Git history** (the archive has no `.git`), so past secret leaks cannot be ruled out. Rotate any secret that was ever committed or pasted.
- **Dependency vulnerabilities** (no `npm audit` / OWASP scan offline) and whether pinned versions (Vite 8, `nitro` beta, Spring Boot 3.5.0, Pcap4J 1.8.2) have advisories.
- **Flyway-vs-entity parity on real PostgreSQL** (tests never ran against it) and `validate` startup success.
- **VLAN/fragment/tunnel decoding** behaviour of Pcap4J on real traffic (M8 is inferred from `PacketDecoder`'s branches).
- **Performance** on large captures (5 M rows, index write cost, memory use of 13 rule evaluators with `max-tracked-keys` = 200 k each).
- **Accessibility, browser compatibility and visual regressions** of the UI.
