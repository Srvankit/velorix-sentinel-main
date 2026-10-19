# Velorix Sentinel

**AI-powered deep packet inspection & network threat detection.**
A product of **Velorix Technologies**.

Velorix Sentinel inspects live traffic and PCAP captures, correlates signals across the
estate and turns raw frames into prioritised, analyst-ready intelligence — with automated
IOC extraction, risk scoring and remediation guidance.

## Status

| Milestone | State |
| --- | --- |
| Design system & foundations | Done |
| Marketing / landing page | Done |
| Authentication module | Done |
| Dashboard, packet analysis, AI chat, uploads | Not started |

No fabricated metrics are rendered anywhere in the UI. Surfaces that will eventually hold
live data render skeleton placeholders or empty states until the backend supplies values.

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
npm i
npm run dev
```

`VITE_API_BASE_URL` should include the `/api` context path. On Netlify, set it to `https://velorix-sentinel-backend.onrender.com/api`; the backend allows the Netlify origin and handles credentialed JSON preflight requests directly. For local development use `http://localhost:8081/api`.


## Running the stack locally

1. Start PostgreSQL or create a Supabase project. For Supabase, use the session-pooler JDBC URL and credentials documented in `backend/README.md`.
2. Copy `backend/.env.example`, set `JWT_SECRET` (`openssl rand -base64 64`) and the database credentials, then run `mvn spring-boot:run` from `backend/`.
3. Run the frontend with `bun run dev`. It always calls the absolute `VITE_API_BASE_URL`; point it at `http://localhost:8081/api` only if you deliberately want the local API instead of the deployed one.

Backend tests (`mvn test`) cover registration, login, `/auth/me`, refresh rotation, logout, email verification and password reset against an in-memory database.

---

Built by Velorix Technologies.
