# Velorix Sentinel Backend

Production-ready backend foundation for **Velorix Sentinel**, the AI powered deep packet
inspection and network threat detection platform by **Velorix Technologies**.

This milestone ships **architecture and infrastructure only**. Authentication business logic
(registration, login, token issuance) is intentionally not implemented yet — the endpoints,
services, mappers and security plumbing they will plug into are all in place.

## Stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA / Hibernate · PostgreSQL ·
Maven · Lombok · MapStruct · Jakarta Validation · springdoc OpenAPI · SLF4J

## Package layout

```
com.velorix.sentinel
├── common          ApiResponse, ApiError, PageResponse
├── config          SecurityConfig, CorsConfig, JpaAuditingConfig, properties/
├── constants       ApiConstants, SecurityConstants, MessageConstants
├── controller      AuthController, HealthController
├── documentation   OpenApiConfig (Swagger)
├── dto             auth/ request + response records
├── entity          BaseEntity, User, RefreshToken, ActivityLog, enums/
├── exception       ApiException hierarchy + GlobalExceptionHandler
├── mapper          MapStruct mappers
├── repository      Spring Data repositories
├── security        JwtTokenProvider, JwtAuthenticationFilter, entry point, access denied handler
├── service         AuthService + impl/
├── util            RequestUtils, TokenUtils
└── validation      @StrongPassword + validator
```

## Configuration

All configuration comes from environment variables — nothing is hardcoded. Copy
`.env.example` and export the values (or supply them through your deployment platform).

| Variable | Purpose |
| --- | --- |
| `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD` | Supabase PostgreSQL connection (preferred for Render) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Fallback PostgreSQL connection for local development |
| `JWT_SECRET` | A stable secret; preferably Base64 from `openssl rand -base64 64` |
| `JWT_ACCESS_TOKEN_EXPIRATION` / `JWT_REFRESH_TOKEN_EXPIRATION` | ISO-8601 durations (`PT15M`, `P7D`) |
| `CORS_ALLOWED_ORIGINS` | Comma separated frontend origins |
| `FRONTEND_BASE_URL` | Public frontend URL used in verification and password-reset links |

For Render, configure the web service health check path as `/api/health` (or
`/actuator/health`). Both endpoints are public, lightweight liveness checks and
must return HTTP 200 before Render keeps the service running.

### Supabase PostgreSQL

The backend uses JPA and Flyway against PostgreSQL; Supabase works without an
authentication-code rewrite. In the Supabase dashboard, open **Connect** and
choose the **Session pooler** connection details. Use the host, port, database,
username, and password to build a JDBC URL:

```text
SUPABASE_DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<supabase-database-password>
```

Use port `5432` (session pooler) for this Spring/JPA application. Do not use
the Supabase `anon` key or `service_role` key as the database password, and do
not paste either key into Render. Flyway creates the application's `users`,
`refresh_tokens`, and `activity_logs` tables on the first deployment. Existing
data is not copied automatically; export/import it separately if needed.

### Google and GitHub sign-in

OAuth sign-in is enabled when the provider client variables are configured. Add these Render
environment variables (never commit the secrets):

```text
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_SCOPE=read:user,user:email
```

Register these callback URLs with the providers:

```text
https://velorix-sentinel-backend.onrender.com/login/oauth2/code/google
https://velorix-sentinel-backend.onrender.com/login/oauth2/code/github
```

The frontend starts OAuth through `/api/oauth2/authorization/{provider}` and receives the
short-lived access token in the callback URL fragment, so it is not sent in HTTP referrers.
| `SERVER_PORT`, `LOG_LEVEL_APP`, `JPA_DDL_AUTO` | Runtime tuning |

## Database

Hibernate runs with `ddl-auto=validate`; the schema is owned by migrations.
The baseline lives at `src/main/resources/db/migration/V1__baseline_schema.sql` and is
Flyway-compatible naming, so adding the Flyway starter later requires no restructuring.

```bash
createdb velorix_sentinel
psql -d velorix_sentinel -f src/main/resources/db/migration/V1__baseline_schema.sql
```

Tables: `users`, `refresh_tokens`, `activity_logs`.

## Security model

- Stateless — no HTTP session, CSRF disabled, JWT bearer tokens only.
- Passwords hashed with BCrypt (strength 12).
- Roles: `ADMIN`, `ANALYST`, `USER` (stored as `ROLE_*` authorities).
- Public: `/api/auth/**`, `/api/health`, `/actuator/health`, Swagger.
- Protected: `/api/v1/**` and everything else requires a valid access token.
- `JwtAuthenticationFilter` populates the security context; `JwtAuthenticationEntryPoint` and
  `JwtAccessDeniedHandler` return the standard envelope for 401/403.

## API response envelope

Every endpoint — success or failure — returns:

```json
{
  "success": true,
  "message": "Authenticated successfully",
  "data": { },
  "timestamp": "2026-07-30T10:15:30Z",
  "status": 200,
  "errors": null
}
```

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Exchange credentials for tokens |
| POST | `/api/auth/refresh` | Rotate refresh token |
| POST | `/api/auth/logout` | Revoke refresh token |
| POST | `/api/auth/forgot-password` | Send reset link |
| POST | `/api/auth/reset-password` | Complete reset |
| GET | `/api/auth/me` | Authenticated profile |
| GET | `/api/health` | Liveness probe |

Auth flows currently return `501 Not Implemented` by design.

## Running

```bash
cd backend
./mvnw spring-boot:run   # or: mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Built by Velorix Technologies.
