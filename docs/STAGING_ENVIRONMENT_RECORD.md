# Velorix Sentinel — Staging Environment Record

> **Purpose:** Authoritative deployment and asset ledger for the Velorix Sentinel Staging environment.  
> **Instruction for Repository Owner:** Fill in the table below once the staging infrastructure is provisioned on Supabase, Render, Netlify, and OAuth providers as described in [`docs/STAGING_SETUP.md`](file:///docs/STAGING_SETUP.md).

---

## 1. Staging Infrastructure Inventory

| Resource / Service | Provider | Staging Value / Hostname / Reference | Provisioned Date | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend Web App (SPA)** | Netlify | `https://velorix-sentinel-staging.netlify.app` *(or custom domain)* | `YYYY-MM-DD` | `[Pending / Active]` |
| **Frontend Branch** | GitHub / Netlify | `staging` *(or pinned feature branch)* | `YYYY-MM-DD` | `[Pending / Active]` |
| **Backend Web Service** | Render | `https://velorix-sentinel-backend-staging.onrender.com` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Backend Instance Tier** | Render | `[Free / Starter]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Database Project Name** | Supabase | `velorix-sentinel-staging` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Supabase Project Ref** | Supabase | `[e.g. abcdefghijklmnopqrst]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Database Pooler Host** | Supabase | `aws-0-[region].pooler.supabase.com:5432` *(Session Pooler)* | `YYYY-MM-DD` | `[Pending / Active]` |
| **Database Region** | Supabase | `[e.g. us-east-1]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Storage Bucket Name** | Supabase Storage | `velorix-captures-staging` *(Private)* | `YYYY-MM-DD` | `[Pending / Active]` |
| **S3 Gateway Endpoint** | Supabase S3 | `https://[project-ref].supabase.co/storage/v1/s3` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Email Service Provider** | SendGrid / Resend | `[e.g. smtp.sendgrid.net:587]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Email Verified Sender** | Provider / DNS | `noreply@staging.velorix.net` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Google OAuth Client ID** | Google Cloud | `[staging-client-id].apps.googleusercontent.com` | `YYYY-MM-DD` | `[Pending / Active]` |
| **GitHub OAuth Client ID**| GitHub Developers| `[staging-client-id]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **Database Backup & PITR** | Supabase | `[Daily / PITR Enabled / Manual Dump]` | `YYYY-MM-DD` | `[Pending / Active]` |
| **S3 Capture Bucket Backup** | Supabase Storage / S3 | `[Periodic Sync / Secondary Bucket / Manual]` | `YYYY-MM-DD` | `[Pending / Active]` |

---

## 2. Preflight Verification History

Record executions of `./scripts/staging-preflight.sh` or `.\scripts\staging-preflight.ps1` below:

| Date | Executed By | Target Frontend | Target Backend | Checks Passed | Result (PASS/FAIL) | Notes / Remediations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| *Template* | *DevOps* | *https://...* | *https://...* | *10 / 10* | *PASS* | *Initial preflight passed cleanly.* |
