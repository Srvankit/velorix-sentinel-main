# Velorix Sentinel — Architecture

AI-powered deep packet inspection and network threat detection platform, delivered as a
React/TanStack Start frontend against a Spring Boot 3.5 (Java 21) backend with PostgreSQL.

---

## 1. System overview

```text
 Browser ──HTTPS──> TanStack Start (SSR + client router on Netlify)
                        │ axios apiClient (JWT access token, refresh queue)
                        ▼
             Spring Boot API (Render containerized service)
                        │ JPA / JdbcTemplate bulk writers    │ AWS S3 SDK (v2.29)
                        ▼                                    ▼
       Supabase PostgreSQL 16 (pgBouncer pooler)   Supabase Storage (S3-compatible)
       (users, files, packets, threats, analyses)  (raw PCAP/PCAPNG capture files)
```

- Stateless auth: short-lived JWT access token + rotating refresh token.
- Packet capture files are uploaded, validated (extension, magic-byte signatures, size, SHA-256 dedupe),
  stored durably in S3-compatible object storage (or local filesystem in dev), parsed with Pcap4J,
  and persisted as packet/threat/analysis rows.
- Deep Packet Inspection engine executes 13 rule-based threat heuristics asynchronously.
- All security/analytics screens read from owner-scoped endpoints (`CurrentUserProvider.ownerScope()`); nothing is faked in the UI.

## 2. Frontend structure

| Path | Responsibility |
| --- | --- |
| `src/routes/` | File-based routes (85). Each leaf owns its `head()` metadata. |
| `src/layouts/` | `AuthLayout` (public auth shell) and `DashboardLayout` (sidebar + `<main>`). |
| `src/components/` | Feature libraries: `landing`, `dashboard`, `upload`, `analysis`, `soc`, `incidents`, `intel`, `governance`, `admin`, `ops`, plus `common` and shadcn `ui`. |
| `src/services/` | One module per backend domain; the only place axios is called. |
| `src/hooks/` | TanStack Query wrappers and workspace state (`useDashboard`, `useAdminResource`, …). |
| `src/data/` | Static reference catalogues (MITRE, compliance frameworks, RBAC roles, monitored services). |
| `src/lib/`, `src/utils/` | `apiClient`, error classification, `apiConfig` environment detection, formatters. |
| `src/types/` | Shared domain models mirrored from backend DTOs. |

### Routing and guards

TanStack Router file routes. Public surface: landing, auth pages, error states.
Everything else renders behind `ProtectedRoute`, which redirects unauthenticated users to
`/login` and distinguishes session-expired vs unauthorized outcomes.

### Data flow

Component → hook (TanStack Query) → service → `apiClient`. Modules whose backend is not yet
live are gated by readiness flags (`SOC_BACKEND_READY`, `GOVERNANCE_BACKEND_READY`,
`ADMIN_BACKEND_READY`, observability flags) and render explicit "Awaiting Live Data" states
instead of fabricated values.

### Error handling

`src/lib/errors.ts` classifies every failure (network unreachable, CORS, 401/403, validation,
server) into a user-facing message. `apiClient` owns the 401 refresh queue: a single refresh
call is issued and queued requests replay once it resolves. Route-level `ErrorBoundary`
components isolate crashes to the failing panel.

## 3. Design system

Dark-first theme defined entirely with oklch tokens in `src/styles.css`.

- Background near-black `--background`; layered `--surface` / `--elevated` / `--card`.
- Crimson accent `--primary`; `--primary-bright` is the accessible variant for small text
  on dark surfaces (AA contrast).
- Typography: Space Grotesk (display), DM Sans (body), JetBrains Mono (telemetry/console).
- Utilities: `focus-ring`, `glow-ring`, `hover-glow`, gradient surfaces.
- Components never hardcode color utilities; only semantic tokens are used.

## 4. Accessibility and motion

- Every interactive icon-only control carries an `aria-label`.
- One `<main>` landmark per page, provided by the layouts.
- Lists use real `ul`/`ol` + `li`; status placeholders wrap lists in a `role="status"` container
  rather than putting the role on the list element.
- `min-h-dvh` everywhere instead of `min-h-screen`.
- `prefers-reduced-motion` is honoured globally in CSS, and framer-motion effects
  (`Reveal`, `ParticleField`) opt out of animation when the OS requests reduced motion.

## 5. Performance

- Route-level code splitting via TanStack Router; Recharts and markdown rendering are lazy.
- Packet tables use virtualized rows; large aggregates are computed server-side with
  aggregate SQL, never in the browser.
- Packet ingestion uses `JdbcTemplate` batch inserts (`PacketBatchWriter`).

## 6. Backend layout

```text
config/ security/ controller/ service/ repository/ entity/ dto/ mapper/ exception/ storage/ health/
```

- Stateless Spring Security, BCrypt hashing, JWT filter chain, role model `ADMIN | ANALYST | USER`.
- `GlobalExceptionHandler` returns the shared `ApiResponse` envelope for every error.
- Flyway migrations own the schema; JPA auditing stamps created/updated metadata.
- File search uses the JPA Criteria API (`UploadedFileSpecifications`) so PostgreSQL binds
  parameters as `varchar` — preventing the historical `lower(bytea)` failure.

## 7. Supabase Row Level Security (RLS) & Access Governance

Velorix Sentinel utilizes **Supabase PostgreSQL 16**. To protect against unauthorized access via PostgREST or external clients:

1. **RLS Activation:** Row Level Security is enabled on all tables:
   `users`, `uploaded_files`, `packets`, `threat_findings`, `analyses`, `activity_logs`, `refresh_tokens`, `verification_tokens`, `detection_rules`.
2. **Public API Role Revocation:** Direct permissions for anonymous (`anon`) and authenticated (`authenticated`) PostgREST roles are revoked (`REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated`).
3. **Backend Service Role Mediation:** The Spring Boot backend connects over pgBouncer session pooler credentials (`SUPABASE_DB_URL`). Application queries run with dedicated service privileges, with all authorization enforced within the application layer via `CurrentUserProvider.ownerScope()`.

## 8. Object Storage Architecture

Capture file ingestion supports dual-mode storage via `FileStorageService`:

1. **Local Filesystem (`velorix.storage.provider=filesystem`):**
   - Configured via `CAPTURE_STORAGE_LOCATION` (default `./data/captures`).
   - Used for local development and offline unit testing.
2. **S3-Compatible Object Store (`velorix.storage.provider=s3`):**
   - Integrates with Supabase Storage, AWS S3, Cloudflare R2, or MinIO via AWS SDK for Java v2 (`software.amazon.awssdk:s3`).
   - Configured via `S3_BUCKET_NAME`, `AWS_REGION`, `S3_ENDPOINT`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
   - Supports path-style access (`S3_PATH_STYLE_ACCESS=true`) required for Supabase and MinIO.
   - Decoupled from container storage: Container restarts or scale-out replicas do not lose capture data.
   - Enforces user quotas: maximum 10 capture files and 500 MB total storage per user account.

## 9. Environments & Production Guardrails

### Operational Environment Variables

| Variable | Scope | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Backend | Set to `prod` in production deployments. |
| `SUPABASE_DB_URL` / `DB_URL` | Backend | PostgreSQL 16 JDBC URL (Supabase session pooler on port 5432 or 6543). |
| `SUPABASE_DB_USERNAME` / `DB_USERNAME` | Backend | Database username (`postgres`). |
| `SUPABASE_DB_PASSWORD` / `DB_PASSWORD` | Backend | Strong database password (default credentials rejected in prod). |
| `JWT_SECRET` | Backend | Base64-encoded secret of at least 64 bytes (512 bits) for HMAC-SHA512. |
| `FRONTEND_BASE_URL` | Backend | Public HTTPS URL of the web console (e.g. `https://velorixsentinel.netlify.app`). |
| `CORS_ALLOWED_ORIGINS` | Backend | Explicit exact HTTPS origins allowed for CORS (e.g. `https://velorixsentinel.netlify.app`). |
| `VELORIX_STORAGE_PROVIDER` | Backend | Storage backend (`filesystem` in dev, `s3` in prod). |
| `S3_BUCKET_NAME` / `SUPABASE_STORAGE_BUCKET` | Backend | Target bucket for capture uploads. |
| `S3_ENDPOINT` | Backend | S3-compatible endpoint (e.g. `https://<ref>.supabase.co/storage/v1/s3`). |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Backend | Credentials for S3 storage (or container IAM role). |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Backend | Optional SMTP configuration for transactional email. |
| `VITE_API_BASE_URL` | Frontend | Backend API origin (`https://velorix-sentinel-backend.onrender.com/api`). |

### Production Profile Validation & Guardrails

When `SPRING_PROFILES_ACTIVE=prod` is active, `ProductionProfileValidator` executes during application startup to enforce fail-fast security guardrails:
- **JWT Secret:** Rejects missing, non-Base64, < 64 bytes, or placeholder keys.
- **Database:** Rejects localhost, 127.0.0.1, H2 in-memory URLs, and insecure default passwords.
- **Frontend URL:** Requires HTTPS scheme; rejects localhost.
- **CORS:** Rejects wildcards (`*`) and wildcards on multi-tenant platforms (`*.netlify.app`, `*.lovable.app`).
- **Storage:** If provider is `s3`, validates bucket and credential completeness.
- **Actuator:** Prometheus metrics scraper restricted to `ROLE_ADMIN`; health probes segregated into `liveness` (`ping`) and `readiness` (`db,diskSpace,storage,inspectionQueue`).
- **Secret Redaction:** Validation error messages and logs never print raw secret or password values.

## 10. Conventions

- Services are the only axios consumers; components never call HTTP directly.
- New domain data starts as a typed service + hook with a readiness flag and an
  `AwaitingData` empty state.
- Reference/static catalogues live in `src/data/`, never inline in components.
- Every new route defines unique `head()` metadata.
