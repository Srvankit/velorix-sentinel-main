# Velorix Sentinel — Architecture

AI-powered deep packet inspection and network threat detection platform, delivered as a
React/TanStack Start frontend against a Spring Boot 3.5 (Java 21) backend with PostgreSQL.

---

## 1. System overview

```text
 Browser ──HTTPS──> TanStack Start (SSR + client router)
                        │ axios apiClient (JWT access token, refresh queue)
                        ▼
            Spring Boot API  https://velorix-sentinel-backend.onrender.com
                        │ JPA / JdbcTemplate bulk writers
                        ▼
              PostgreSQL (Neon) — users, files, packets, threats, analyses
```

- Stateless auth: short-lived JWT access token + rotating refresh token.
- Packet capture files are uploaded, validated (extension, MIME, size, SHA-256 dedupe),
  parsed with Pcap4J, and persisted as packet/threat/analysis rows.
- All security/analytics screens read from owner-scoped endpoints; nothing is faked in the UI.

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
config/ security/ controller/ service/ repository/ entity/ dto/ mapper/ exception/
```

- Stateless Spring Security, BCrypt hashing, JWT filter chain, role model `ADMIN | ANALYST | USER`.
- `GlobalExceptionHandler` returns the shared `ApiResponse` envelope for every error.
- Flyway migrations own the schema; JPA auditing stamps created/updated metadata.
- File search uses the JPA Criteria API (`UploadedFileSpecifications`) so PostgreSQL binds
  parameters as `varchar` — the fix for the historical `lower(bytea)` failure.

## 7. Environments

| Variable | Purpose |
| --- | --- |
| `VITE_API_BASE_URL` | Backend origin (production Render URL). |

`src/utils/apiConfig.ts` validates configuration at startup and detects cross-origin preview
hosts so CORS problems surface as actionable messages rather than opaque network errors.

## 8. Conventions

- Services are the only axios consumers; components never call HTTP directly.
- New domain data starts as a typed service + hook with a readiness flag and an
  `AwaitingData` empty state.
- Reference/static catalogues live in `src/data/`, never inline in components.
- Every new route defines unique `head()` metadata.
