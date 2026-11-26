# Velorix Sentinel — Production Environment Variables Checklist

This document is the authoritative checklist of all environment variables required to deploy and operate Velorix Sentinel in production. Every variable corresponds to actual configuration in `application.yml`, `application-prod.yml`, and `ProductionProfileValidator.java`.

---

## 1. Secrets Separation Policy (Staging vs. Production)

> [!CRITICAL]
> Production MUST NOT share any database, S3 storage bucket, JWT signing secret, OAuth2 client credentials, or SMTP API key with staging. Reusing credentials across environments will compromise production tenant isolation.

### Cryptographic Secret Generation Commands

#### 1. JWT Secret (`JWT_SECRET`)
Must be at least 64 cryptographically random bytes (512 bits), Base64-encoded:
- **Bash / macOS / Linux:**
  ```bash
  openssl rand -base64 64 | tr -d '\n'
  ```
- **Windows PowerShell 5.1:**
  ```powershell
  $bytes = New-Object byte[] 64; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes); [Convert]::ToBase64String($bytes)
  ```

#### 2. Database Password (`SUPABASE_DB_PASSWORD`)
Generate a strong alphanumeric password of at least 32 characters:
- **Bash:**
  ```bash
  openssl rand -base64 32 | tr -d '=+/\n' | cut -c1-32
  ```
- **PowerShell 5.1:**
  ```powershell
  $chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+'
  -join ((1..32) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
  ```

---

## 2. Master Production Environment Variables Matrix

| # | Variable Name | Scope | Mandatory? | Secret? | Target Platform | Expected Value / Format | Verified Against Code / Default | Set? | Verified? |
|---|---|---|---|---|---|---|---|:---:|:---:|
| 1 | `SPRING_PROFILES_ACTIVE` | Backend | **Mandatory** | No | Render Web Service | `prod` | `application-prod.yml:7` | [ ] | [ ] |
| 2 | `SUPABASE_DB_URL` | Backend | **Mandatory** | No | Render Web Service | `jdbc:postgresql://aws-0-<PROD_REGION>.pooler.supabase.com:5432/postgres?sslmode=require` | `ProductionProfileValidator.java:168` | [ ] | [ ] |
| 3 | `SUPABASE_DB_USERNAME` | Backend | **Mandatory** | No | Render Web Service | `postgres.<PROD_PROJECT_REF>` | `application.yml` (`spring.datasource.username`) | [ ] | [ ] |
| 4 | `SUPABASE_DB_PASSWORD` | Backend | **Mandatory** | **YES** | Render Web Service | Strong password (min 32 chars) | `ProductionProfileValidator.java:181` | [ ] | [ ] |
| 5 | `JWT_SECRET` | Backend | **Mandatory** | **YES** | Render Web Service | 64-byte Base64 string | `ProductionProfileValidator.java:132-159` | [ ] | [ ] |
| 6 | `JWT_ISSUER` | Backend | Optional | No | Render Web Service | `velorix-sentinel-prod` | `JwtProperties.java` (Default: `velorix-sentinel`) | [ ] | [ ] |
| 7 | `JWT_ACCESS_TOKEN_EXPIRATION` | Backend | Optional | No | Render Web Service | `PT15M` (15 minutes) | `JwtProperties.java` | [ ] | [ ] |
| 8 | `JWT_REFRESH_TOKEN_EXPIRATION` | Backend | Optional | No | Render Web Service | `P7D` (7 days) | `JwtProperties.java` | [ ] | [ ] |
| 9 | `FRONTEND_BASE_URL` | Backend | **Mandatory** | No | Render Web Service | `https://<PROD_FRONTEND_DOMAIN>` | `ProductionProfileValidator.java:191-204` | [ ] | [ ] |
| 10 | `CORS_ALLOWED_ORIGINS` | Backend | **Mandatory** | No | Render Web Service | `https://<PROD_FRONTEND_DOMAIN>` (no wildcards) | `ProductionProfileValidator.java:206-228` | [ ] | [ ] |
| 11 | `VELORIX_STORAGE_PROVIDER` | Backend | **Mandatory** | No | Render Web Service | `s3` | `ProductionProfileValidator.java:231` | [ ] | [ ] |
| 12 | `S3_BUCKET_NAME` | Backend | **Mandatory** | No | Render Web Service | `velorix-captures-prod` | `ProductionProfileValidator.java:240` | [ ] | [ ] |
| 13 | `AWS_REGION` | Backend | **Mandatory** | No | Render Web Service | Supabase region (e.g. `us-east-1`) | `ProductionProfileValidator.java:245` | [ ] | [ ] |
| 14 | `S3_ENDPOINT` | Backend | **Mandatory** | No | Render Web Service | `https://<PROD_PROJECT_REF>.supabase.co/storage/v1/s3` | `StorageProperties.java` | [ ] | [ ] |
| 15 | `AWS_ACCESS_KEY_ID` | Backend | **Mandatory** | **YES** | Render Web Service | Supabase S3 Access Key ID | `ProductionProfileValidator.java:256` | [ ] | [ ] |
| 16 | `AWS_SECRET_ACCESS_KEY` | Backend | **Mandatory** | **YES** | Render Web Service | Supabase S3 Secret Key | `ProductionProfileValidator.java:257` | [ ] | [ ] |
| 17 | `S3_PATH_STYLE_ACCESS` | Backend | Optional | No | Render Web Service | `true` | `ProductionProfileValidator.java:249-254` | [ ] | [ ] |
| 18 | `S3_PREFIX` | Backend | Optional | No | Render Web Service | `captures/` | `StorageProperties.java` | [ ] | [ ] |
| 19 | `SMTP_HOST` | Backend | **Mandatory** | No | Render Web Service | `smtp.resend.com` or custom SMTP host | `ProductionProfileValidator.java:272` | [ ] | [ ] |
| 20 | `SMTP_PORT` | Backend | Optional | No | Render Web Service | `587` | `application.yml` (`spring.mail.port`) | [ ] | [ ] |
| 21 | `SMTP_USERNAME` | Backend | **Mandatory** | No | Render Web Service | `resend` or SMTP username | `application.yml` (`spring.mail.username`) | [ ] | [ ] |
| 22 | `SMTP_PASSWORD` | Backend | **Mandatory** | **YES** | Render Web Service | Resend API Key (`re_...`) or SMTP password | `application.yml` (`spring.mail.password`) | [ ] | [ ] |
| 23 | `MAIL_FROM` | Backend | Optional | No | Render Web Service | `notifications@<PROD_VERIFIED_DOMAIN>` | `application.yml` (`velorix.mail.from`) | [ ] | [ ] |
| 24 | `MAIL_FROM_NAME` | Backend | Optional | No | Render Web Service | `Velorix Sentinel` | `application.yml` (`velorix.mail.from-name`) | [ ] | [ ] |
| 25 | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Backend | Optional | No | Render Web Service | Google OAuth Prod Client ID | `SecurityConfig.java` | [ ] | [ ] |
| 26 | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Backend | Optional | **YES** | Render Web Service | Google OAuth Prod Client Secret | `SecurityConfig.java` | [ ] | [ ] |
| 27 | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` | Backend | Optional | No | Render Web Service | GitHub OAuth Prod Client ID | `SecurityConfig.java` | [ ] | [ ] |
| 28 | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET` | Backend | Optional | **YES** | Render Web Service | GitHub OAuth Prod Client Secret | `SecurityConfig.java` | [ ] | [ ] |
| 29 | `BETA_INVITE_ONLY` | Backend | **Mandatory** | No | Render Web Service | `true` (soak) / `false` (public launch) | `BetaProperties.java` | [ ] | [ ] |
| 30 | `BETA_ALLOWED_EMAILS` | Backend | Optional | No | Render Web Service | `tester1@example.com, @partner.org` | `BetaProperties.java` | [ ] | [ ] |
| 31 | `ALLOW_LOGGING_EMAIL_FALLBACK` | Backend | **CRITICAL** | No | Render Web Service | **`false`** (MUST stay false in prod) | `ProductionProfileValidator.java:273-281` | [ ] | [ ] |
| 32 | `ALLOW_FILESYSTEM_STORAGE` | Backend | **CRITICAL** | No | Render Web Service | **`false`** (MUST stay false in prod) | `ProductionProfileValidator.java:232-267` | [ ] | [ ] |
| 33 | `TRUSTED_PROXIES` | Backend | Optional | No | Render Web Service | Cloudflare / Render proxy CIDR ranges | `application.yml` (`velorix.security.trusted-proxies`) | [ ] | [ ] |
| 34 | `PORT` | Backend | Optional | No | Render Web Service | `10000` | Render default container port | [ ] | [ ] |
| 35 | `SWAGGER_ENABLED` | Backend | Optional | No | Render Web Service | `false` | `ProductionProfileValidator.java:286-291` | [ ] | [ ] |
| 36 | `LOG_LEVEL_APP` | Backend | Optional | No | Render Web Service | `INFO` | `application-prod.yml:48` | [ ] | [ ] |
| 37 | `LOG_STRUCTURED_FORMAT` | Backend | Optional | No | Render Web Service | `json` | `application-prod.yml:45` | [ ] | [ ] |
| 38 | `VITE_API_BASE_URL` | Frontend | **Mandatory** | No | Netlify Site Settings | `https://<PROD_BACKEND_DOMAIN>/api` | Evaluated at Netlify **BUILD TIME** | [ ] | [ ] |
| 39 | `VITE_FEEDBACK_EMAIL` | Frontend | Optional | No | Netlify Site Settings | `support@example.com` | Evaluated at Netlify **BUILD TIME** | [ ] | [ ] |

---

## 3. Rate Limiting Tuning Knobs (Defaults in `application.yml`)

These variables have production-grade defaults in `application.yml` and only require setting if rate limits need manual override:

| Variable Name | Default | Scope / Protection Target |
|---|---|---|
| `RATE_LIMIT_ENABLED` | `true` | Master rate limiting switch |
| `RATE_LIMIT_LOGIN_IP_CAPACITY` | `5` / `PT1M` | Brute force IP protection on `/api/auth/login` |
| `RATE_LIMIT_LOGIN_EMAIL_CAPACITY` | `10` / `PT1M` | Targeted account protection on `/api/auth/login` |
| `RATE_LIMIT_REGISTER_IP_CAPACITY` | `3` / `PT1H` | Registration spam protection on `/api/auth/register` |
| `RATE_LIMIT_FORGOT_PASSWORD_CAPACITY` | `3` / `PT15M` | Email enumeration protection on `/api/auth/forgot-password` |
| `RATE_LIMIT_FILE_UPLOAD_CAPACITY` | `10` / `PT1H` | Ingestion bandwidth protection on `/api/captures/upload` |
| `RATE_LIMIT_GENERAL_API_CAPACITY` | `120` / `PT1M` | General API client burst protection |
| `RATE_LIMIT_MAX_BUCKETS` | `50000` | In-memory token bucket cache size bound |

---

## 4. OAuth2 Redirect URIs Configuration

Configure these EXACT Authorized Redirect URIs in the Google Cloud Console and GitHub Developer Settings for the Production OAuth Applications:

- **Google Cloud Console:**
  `https://<PROD_RENDER_HOST>/login/oauth2/code/google`
- **GitHub Developer Settings:**
  `https://<PROD_RENDER_HOST>/login/oauth2/code/github`

---

## 5. Secret Rotation Procedures & Rollback Instructions

### A. Rotating Database Credentials (`SUPABASE_DB_PASSWORD`)
1. Log into Supabase Dashboard > Project Settings > Database.
2. Under "Database password", click **Reset database password**.
3. Update `SUPABASE_DB_PASSWORD` in the Render Production Web Service environment tab.
4. Trigger a zero-downtime redeploy in Render.
5. *Rollback:* If the application cannot connect, revert the password in Supabase or Render immediately.

### B. Rotating JWT Signing Secret (`JWT_SECRET`)
1. Generate a fresh 64-byte Base64 key using the command in Section 1.
2. Update `JWT_SECRET` in Render.
3. Trigger a redeploy.
4. *Effect:* Existing access tokens and refresh tokens will be invalidated immediately. All users will be required to re-authenticate (expected behavior during security rotation).
5. *Rollback:* Restoring the previous `JWT_SECRET` immediately restores validity to unexpired refresh tokens.

### C. Rotating Supabase S3 Access Keys (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
1. Log into Supabase Dashboard > Storage > S3 Access Keys.
2. Generate a new S3 Access Key pair. Do not delete the old key yet.
3. Update `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` in Render.
4. Trigger a redeploy and verify upload/download via preflight check.
5. Delete the old key in Supabase Dashboard only after verifying production operation.
