# Velorix Sentinel — Production Profile Security Guidelines & Secret Guardrails

**Scope:** Backend Production Deployment (Spring Boot 3.5.x, Java 21) & Frontend (TanStack Start / Netlify)  
**Task Reference:** Task 6.7 (M14) — Production Profile Validation & Secret Guardrails  
**Author:** Velorix Technologies  

---

## 1. Overview & Purpose

Velorix Sentinel enforces strict production profile validation and secret guardrails to ensure that when deployed to production hosting environments (e.g. Render, Docker, Kubernetes), the application:
1. **Fails Fast on Startup:** If any mandatory production secret or operational variable is missing, malformed, or using an insecure development default, application startup halts immediately.
2. **Eliminates Secret Exposure:** Validation errors, application logs, and HTTP error responses are sanitized so that raw secret values, database passwords, or private tokens are **never** logged or emitted.
3. **Guarantees Production Hardening:** Development-only conveniences (e.g., in-memory H2 database, localhost bindings, wildcard CORS origins, public Swagger UI) are strictly prohibited in the production profile.

---

## 2. Production Profile Activation

To activate the hardened production profile, set the following environment variable in your production hosting platform:

```sh
SPRING_PROFILES_ACTIVE=prod
```

When activated, Spring Boot loads:
- Base configuration: [`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml)
- Profile-specific overlay: [`backend/src/main/resources/application-prod.yml`](../backend/src/main/resources/application-prod.yml)
- Startup validation engine: `com.velorix.sentinel.config.ProductionProfileValidator`

---

## 3. Mandatory Production Environment Variables

| Variable | Required in Prod? | Expected Format / Constraint | Example (Placeholder) |
| :--- | :---: | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | **Yes** | Must include `prod` or `production`. | `prod` |
| `JWT_SECRET` | **Yes** | Cryptographically random Base64 string decoding to **at least 64 bytes (512 bits)** for HMAC-SHA512. Known test or placeholder values are rejected. | Generated via `openssl rand -base64 64` |
| `SUPABASE_DB_URL` / `DB_URL` | **Yes** | Remote PostgreSQL 16 JDBC connection string starting with `jdbc:postgresql://`. Must **not** point to `localhost`, `127.0.0.1`, or `jdbc:h2:`. | `jdbc:postgresql://db.<REF>.supabase.co:5432/postgres` |
| `SUPABASE_DB_USERNAME` / `DB_USERNAME` | **Yes** | Database user with access to PostgreSQL schema. | `postgres` |
| `SUPABASE_DB_PASSWORD` / `DB_PASSWORD` | **Yes** | High-entropy production password. Insecure defaults (`postgres`, `password`, `admin`, `root`) are rejected. | Strong production credential |
| `FRONTEND_BASE_URL` | **Yes** | Absolute HTTPS URL of the public web console. Used to construct password reset and email verification links. Must not point to `localhost`. | `https://velorixsentinel.netlify.app` |
| `CORS_ALLOWED_ORIGINS` | **Yes** | Comma-separated list of exact HTTPS origins allowed to make browser API requests. Wildcards (`*` or `*.netlify.app`) are rejected. | `https://velorixsentinel.netlify.app` |
| `VELORIX_STORAGE_PROVIDER` | **Yes** | Storage provider: `s3` for production, `filesystem` for single-node development. | `s3` |
| `S3_BUCKET_NAME` | **If s3** | Target bucket name in Supabase Storage or AWS S3. | `velorix-captures` |
| `S3_ENDPOINT` | **If Supabase/MinIO** | Custom S3 endpoint URL. | `https://<REF>.supabase.co/storage/v1/s3` |
| `AWS_REGION` | **If s3** | AWS region or default region. | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | **If static creds** | S3 API Access Key ID. | Static key or IAM Role |
| `AWS_SECRET_ACCESS_KEY` | **If static creds** | S3 API Secret Access Key. | Static key or IAM Role |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Optional | Transactional email provider credentials (e.g. Resend, SendGrid, Amazon SES). | Configured if live email delivery is enabled |

---

## 4. Secret Guardrails & Verification Rules

`ProductionProfileValidator` enforces the following guardrails during startup before the server accepts traffic:

### A. JWT Secret Guardrails
- **Minimum Bit-Strength:** HMAC-SHA512 requires at least 512 bits (64 bytes) of key material. The validator decodes the Base64 string and rejects any key smaller than 64 bytes.
- **Placeholder Detection:** The validator inspects the decoded key for common development patterns (e.g. `test-secret`, `placeholder`, `changeme`, `0123456789`). Matching keys are rejected immediately.
- **Key Generation Standard:**
  ```sh
  openssl rand -base64 64
  ```

### B. Database Guardrails
- **Host Verification:** URLs containing `localhost`, `127.0.0.1`, `0.0.0.0`, `::1`, or `jdbc:h2:` trigger immediate startup failure in production.
- **Credential Strength:** Default passwords such as `postgres`, `password`, `admin`, `root`, or `123456` trigger startup failure.
- **URL Redaction:** If connection errors occur, JDBC query parameters containing inline passwords are redacted as `?[REDACTED]`.

### C. CORS & Origin Isolation Guardrails
- **Multi-Tenant Wildcard Elimination:** Wildcard origins on shared platforms (such as `*.netlify.app`, `*.lovable.app`, `*.vercel.app`) are prohibited. An attacker deploying `evil.netlify.app` cannot make authenticated CORS calls to the production API.
- **Strict HTTPS Scheme:** Non-HTTPS origins (e.g. `http://...`) are rejected in production.

### D. Object Storage Durability Guardrails
- Container disks in cloud deployments (e.g. Render, Kubernetes pods) are ephemeral. If `velorix.storage.provider=filesystem` is used in production, a prominent warning is logged. S3-compatible object storage (Supabase Storage) is enforced for multi-instance durability.

### E. Swagger / OpenAPI Documentation Lockdown
- In `application-prod.yml`, `springdoc.swagger-ui.enabled` and `springdoc.api-docs.enabled` default to `false`.
- If explicitly overridden to `true`, the application issues a high-visibility security alert, and Spring Security strictly enforces `ROLE_ADMIN` access on all OpenAPI endpoints.

---

## 5. Fail-Fast Startup Behavior

If any guardrail is violated, `ProductionProfileValidator` throws `com.velorix.sentinel.exception.ProductionConfigurationException`.

Example sanitized startup failure log:
```text
Production configuration validation failed with 3 violation(s):
  - velorix.security.jwt.secret (JWT_SECRET) is missing or empty. A cryptographically random Base64 secret of at least 64 bytes is required.
  - spring.datasource.url points to a local host address. A production PostgreSQL instance (e.g. Supabase session pooler) is required.
  - velorix.app.frontend-base-url points to localhost. The public web console origin (e.g. https://velorixsentinel.netlify.app) is required.
Application startup halted to prevent running in production with insecure or invalid configuration.
```

**Notice:** No secret, password, or key fragment is ever displayed in the output.

---

## 6. Local Development vs. Production Differences

| Feature | Local Development (`dev` or default) | Test (`test`) | Production (`prod`) |
| :--- | :--- | :--- | :--- |
| **Profile** | Default / `dev` | `test` | `prod` |
| **Database** | Local PostgreSQL (`localhost:5432`) | In-memory H2 (`jdbc:h2:mem:sentinel`) | Supabase PostgreSQL 16 (remote pooler) |
| **DDL Policy** | `validate` | `create-drop` | `validate` |
| **Flyway Clean** | Disabled | Disabled | Strictly Disabled (`clean-disabled: true`) |
| **Capture Storage** | Local directory (`./data/captures`) | Temp directory (`/tmp/...`) | Supabase Storage (S3-compatible) |
| **JWT Key** | Configured via `.env` | Test fixture key | High-entropy Base64 key (>= 64 bytes) |
| **Swagger UI** | Available to Admin | Available to Admin | **Disabled** (`404` / `403` DenyAll) |
| **Error Details** | Stack traces hidden | Stack traces hidden | Stack traces & messages strictly hidden |
| **Startup Validator** | Informational logging only | Informational logging only | **Strict Fail-Fast Enforced** |

---

## 7. Secret Scanning & Git Hygiene

- **Repository Cleanliness:** No `.env` files or real production credentials are committed to the repository.
- **Git Tracking Exclusions:** `.gitignore` excludes `.env`, `*.env`, `.env.*`, and credentials files.
- **Example Files:** Only `backend/.env.example` is committed, providing redacted documentation templates with zero real secrets.

