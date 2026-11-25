# Velorix Sentinel

**AI-powered deep packet inspection & network threat detection.**
A product of **Velorix Technologies**.

Velorix Sentinel inspects PCAP and PCAPNG captures, correlates signals across the
estate and turns raw frames into prioritised, analyst-ready intelligence — with automated
IOC extraction, risk scoring and remediation guidance.

## Status

| Milestone | State |
| --- | --- |
| Design system & foundations | Done |
| Marketing / landing page | Done |
| Authentication, MFA & Account Lifecycle | Done |
| PCAP/PCAPNG Uploads & Magic-Byte Validation | Done |
| Streaming Deep Packet Inspection & Pure-Java Decoders | Done |
| Automated Rule-Based Threat Detection (13 Rules) | Done |
| Real-time Threat Intelligence Feed & Risk Badging | Done |
| Network Analytics Dashboard & Telemetry Visualizations | Done |
| Production Hardening (CORS, Swagger Gating, Actuator Probes) | Done |
| Production Profile Validation & Secret Guardrails | Done |
| AI Copilot | Preview / Experimental (Waitlist) |

No fabricated metrics are rendered anywhere in the UI. Live data surfaces display real verdicts computed by the streaming packet inspection and detection engine. Unbuilt post-beta modules are gated behind explicit preview / waitlist status.

## Frontend stack

- TanStack Start (TanStack Router, file-based routing) + Vite
- React 19 + TypeScript
- Tailwind CSS v4 (dark-first theme, crimson `#DC2626` on near-black `#0B0F19`)
- React Hook Form + Zod for every form
- Axios API client with JWT interceptors
- Framer Motion, lucide-react, Recharts

## Backend contract

The frontend talks to a Spring Boot (Java 21) service over `VITE_API_BASE_URL`
(default `/api`). Authentication endpoints consumed today:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Create an account (BCrypt hashed), returns the new profile |
| POST | `/auth/login` | Authenticate, returns a session |
| POST | `/auth/forgot-password` | Send a reset link |
| POST | `/auth/reset-password` | Set a new password from a reset token, revokes all sessions |
| POST | `/auth/verify-email` | Confirm an email address with the emailed token |
| POST | `/auth/resend-verification` | Send a fresh verification link |
| POST | `/auth/refresh` | Rotate the refresh token for a new token pair |
| POST | `/auth/logout` | Revoke the supplied refresh token |
| GET | `/auth/me` | Current user profile |

Every response is expected in the `ApiResponse<T>` envelope
(`{ success, message, data, timestamp }`); the client unwraps it and normalises all
failures into an `ApiError` (`status`, `code`, `message`, `fieldErrors`).

## Routes

Public: `/`, `/login`, `/register`, `/forgot-password`, `/reset-password`,
`/verify-email`, `/session-expired`, `/unauthorized`, plus a branded 404.
Authenticated modules are wrapped in `<ProtectedRoute>`, which supports role gating
(`ROLE_USER`, `ROLE_ANALYST`, `ROLE_ADMIN`).

## Project structure

```
src/
  components/   auth, common (skeletons, states), landing, ui
  context/      AuthContext — JWT session source of truth
  hooks/        useAuth, useHydrated
  layouts/      AuthLayout, DashboardLayout
  routes/       file-based routes (__root, landing, auth pages)
  services/     apiClient (axios + interceptors), authService
  types/        api, auth, threat domain contracts
  utils/        constants, validation (zod), password, storage, format
```

## Development

```sh
npm ci
npm run dev
```

`VITE_API_BASE_URL` should include the `/api` context path. On Netlify, set it to `https://velorix-sentinel-backend.onrender.com/api`; the backend allows the Netlify origin and handles credentialed JSON preflight requests directly. For local development use `http://localhost:8081/api`.


## Running the stack locally

1. Start PostgreSQL or create a Supabase project. For Supabase, use the session-pooler JDBC URL and credentials documented in `backend/README.md`.
2. Copy `backend/.env.example`, set `JWT_SECRET` (`openssl rand -base64 64`) and the database credentials, then run `mvn spring-boot:run` from `backend/`.
3. Run the frontend with `npm run dev`. It always calls the absolute `VITE_API_BASE_URL`; point it at `http://localhost:8081/api` only if you deliberately want the local API instead of the deployed one.

## Automated Test Suites

- **Backend Regression Suite (`mvn test`):** 400+ unit and integration tests covering stateless JWT authentication, token rotation, account lifecycle and GDPR deletion, capture uploads, streaming packet decoding (Ethernet, IPv4, IPv6, ARP, TCP, UDP, DNS, ICMP, HTTP), the 13-rule threat detection engine, inspection pipeline bounding, rate limiting, trusted proxy evaluation, strict production CORS filtering, production Swagger gating, Actuator observability probes, and production profile secret guardrails.
- **Frontend Test Suite (`npm test`):** 126 unit and integration tests executed with `bun:test` covering route-shell gating, AI Copilot disclosure, OAuth callback token exchange, authentication state management, and truthful status components.

## Production Configuration

When deploying the backend to production (e.g. Render, Kubernetes, or Docker), activate the `prod` profile:

```sh
SPRING_PROFILES_ACTIVE=prod
```

The production profile activates `application-prod.yml` and triggers `ProductionProfileValidator` during startup to verify:
- `JWT_SECRET` is present, valid Base64, >= 64 bytes (512 bits), and not a known placeholder.
- `SUPABASE_DB_URL` / `DB_URL` points to a remote PostgreSQL 16 instance (not localhost or H2).
- `SUPABASE_DB_PASSWORD` / `DB_PASSWORD` is configured and not an insecure default.
- `FRONTEND_BASE_URL` uses HTTPS and does not point to localhost.
- `CORS_ALLOWED_ORIGINS` specifies explicit, exact production HTTPS origins (wildcards prohibited).
- Swagger UI and OpenAPI documentation are disabled by default.

## Staging Environment

Velorix Sentinel maintains a staging environment replicating all production security controls, Supabase PostgreSQL persistence, object storage, and strict profile validation.

For the complete step-by-step provisioning runbook, environment variable matrix, and troubleshooting guide, see:
- [Staging Setup Runbook](file:///docs/STAGING_SETUP.md)
- [Staging Environment Record](file:///docs/STAGING_ENVIRONMENT_RECORD.md)
- [Staging Verification & Pre-Flight Testing Guide](file:///docs/STAGING_VERIFICATION.md)
- [Staging Preflight Verification Script (Bash)](file:///scripts/staging-preflight.sh)
- [Staging Preflight Verification Script (PowerShell)](file:///scripts/staging-preflight.ps1)

To run the automated Golden PCAP verification harness against staging:

```sh
# Run all 16 verification checks across 6 stages:
bun run staging:verify

# Or target specific stages:
bun run scripts/staging-verify/index.ts --stage golden
```

To run the automated preflight test suite against deployed staging infrastructure:

```sh
# POSIX Bash:
./scripts/staging-preflight.sh \
  https://velorix-sentinel-staging.netlify.app \
  https://velorix-sentinel-backend-staging.onrender.com

# Windows PowerShell:
.\scripts\staging-preflight.ps1 `
  -FrontendUrl https://velorix-sentinel-staging.netlify.app `
  -BackendUrl https://velorix-sentinel-backend-staging.onrender.com
```

## Disaster Recovery & Backups

Velorix Sentinel maintains automated backup procedures, Point-in-Time Recovery (PITR) documentation, disaster playbooks, and verification tooling:
- [Disaster Recovery Runbook](file:///RUNBOOK_DISASTER_RECOVERY.md)
- [Disaster Recovery Drill Log Template](file:///docs/DR_DRILL_LOG.md)
- [Local Rehearsal & Verification Report](file:///docs/DR_DRILL_RESULTS_LOCAL.md)
- [Database Backup Script (Bash)](file:///scripts/dr/backup-db.sh) / [PowerShell](file:///scripts/dr/backup-db.ps1)
- [Database Restore Script (Bash)](file:///scripts/dr/restore-db.sh) / [PowerShell](file:///scripts/dr/restore-db.ps1)
- [Database Integrity Verification SQL](file:///scripts/dr/verify-integrity.sql)
- [Storage Reconciliation Tool](file:///scripts/dr/reconcile-storage.ts)

---

Built by Velorix Technologies.
