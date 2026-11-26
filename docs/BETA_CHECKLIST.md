# Velorix Sentinel — Closed Beta Launch Checklist & Soak Testing Guide

This checklist governs the launch, tester onboarding, operational monitoring, and 48-hour soak testing procedures for the Velorix Sentinel Closed Beta.

---

## 1. Pre-Beta Go / No-Go Checklist

All items must be verified and signed off before admitting external testers to the platform:

| Check Item | Requirement | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| **1. Staging Verification** | Golden PCAP verification suite passes with 0 unexpected failures. | Run `npm run verify:staging` or Task 8.2 harness against staging. | [ ] |
| **2. Disaster Recovery Readiness** | Restore runbook and scripts verified and ready. | Review `docs/RUNBOOK_DISASTER_RECOVERY.md`; test `restore-db` on local container. | [ ] |
| **3. Object Storage Configured** | S3 / Supabase Storage bucket accessible and writable. | Verify `/actuator/health/readiness` reports `storage: UP`. | [ ] |
| **4. Transactional Email** | SMTP credentials configured and delivering emails. | Register test user and verify activation link delivery. | [ ] |
| **5. Beta Gate Active** | `BETA_INVITE_ONLY=true` active in Render staging environment. | Verify startup log: `Closed beta invite-only gate: ON`. | [ ] |
| **6. Allowlist Populated** | Initial tester emails added to `BETA_ALLOWED_EMAILS`. | Confirm tester emails in env variable; verify unlisted emails receive 403. | [ ] |
| **7. Production Guardrails** | `SPRING_PROFILES_ACTIVE=prod` with no unapproved overrides. | Review startup logs; zero `ProductionConfigurationException` errors. | [ ] |
| **8. Rate Limiting Active** | Bucket4j in-memory token buckets enabled for auth & uploads. | Verify `RATE_LIMIT_ENABLED=true` in environment. | [ ] |
| **9. Uptime Monitoring Active** | External probe pinging `/actuator/health/readiness` every 5m. | Verify UptimeRobot / BetterStack dashboard reports green. | [ ] |
| **10. In-App Feedback Configured** | `VITE_FEEDBACK_EMAIL` baked into frontend build. | Verify "Feedback" / "Send feedback" mailto links appear in UI. | [ ] |

---

## 2. Beta Tester Invitation & Onboarding

### 2.1 Managing the Beta Allowlist

During the closed beta, registration is restricted by email allowlist:

1. **Add Tester to Environment:**
   - Go to Render Dashboard > Staging Backend Web Service > **Environment**.
   - Edit `BETA_ALLOWED_EMAILS`: append the tester's email (comma-separated, e.g. `tester1@example.com, tester2@example.com, @partnerdomain.com`).
   - Save changes (Render will trigger a zero-downtime rolling restart).
2. **Verify Startup:**
   - In Render logs, observe:
     ```text
     INFO  c.v.s.c.ProductionProfileValidator : Closed beta invite-only gate: ON (N allowed entries configured)
     ```

### 2.2 Welcome Message Template for Testers

Send the following email or Slack invite to accepted beta participants:

> **Subject:** Welcome to the Velorix Sentinel Closed Beta!
>
> Hi **[Tester Name]**,
>
> Thank you for participating in the private closed beta of **Velorix Sentinel** — an AI-assisted network packet inspection and threat analysis platform.
>
> ### Getting Started:
> 1. Visit the staging console: **`https://<STAGING_NETLIFY_SITE_NAME>.netlify.app/register`**
> 2. Register using your invited email address: **`[tester@example.com]`** *(Registration is restricted to this exact address)*.
> 3. Check your inbox for the account verification email, click the link, and sign in.
>
> ### Scope & Platform Capabilities:
> - **Offline PCAP / PCAPNG Analysis Only:** Velorix Sentinel is an **offline capture file analysis engine**. It inspects uploaded `.pcap` and `.pcapng` capture files. It does **not** perform live network packet capture or connect directly to network switches/SIEM collectors.
> - **Upload Limits:** Max file size is **100 MB**; storage quota is **10 files** (500 MB total per user).
> - **Supported Protocols:** Ethernet, IPv4, IPv6, TCP, UDP, ICMP, DNS, HTTP, TLS.
>
> ### Providing Feedback:
> - Use the **"Send feedback"** link in the dashboard sidebar or landing footer.
> - Direct bug reports and questions to **`[VITE_FEEDBACK_EMAIL]`**.
>
> Happy hunting,
> The Velorix Security Team

---

## 3. 48-Hour Soak Testing Routine

The 48-hour soak test verifies system stability, memory retention, connection pool hygiene, and reaper task execution over an extended duration on live staging infrastructure.

### 3.1 Observation Schedule & Metrics Table

Record telemetry observations at each checkpoint using `/actuator/prometheus`, Render logs, and Supabase dashboard:

| Checkpoint | Timestamp (UTC) | JVM Heap Used (MB) | DB Active / Idle Conns | Queue Depth | 5xx Errors (last interval) | Stuck Analyses (>15m) | Operator Initials / Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **T+0 (Start)** | | | / | | | | Initial baseline after deploy |
| **T+6 hours** | | | / | | | | Post-onboarding checks |
| **T+12 hours** | | | / | | | | Overnight check 1 |
| **T+24 hours** | | | / | | | | Mid-point health check |
| **T+36 hours** | | | / | | | | Daytime load check |
| **T+48 hours** | | | / | | | | Final soak sign-off |

### 3.2 Key Telemetry Commands for Checkpoints

```bash
# 1. Check Heap Memory & Queue Depth via Prometheus
ADMIN_TOKEN="<ADMIN_JWT>"
curl -s -H "Authorization: Bearer ${ADMIN_TOKEN}" https://api.staging.velorix.net/actuator/prometheus | \
  grep -E "jvm_memory_used_bytes\{area=\"heap\"|inspection_executor_queue_depth|hikaricp_connections_active"

# 2. Check Stuck Analyses via PostgreSQL (see docs/BETA_OPS_QUERIES.sql Query 1)
psql "${SUPABASE_DB_URL}" -c "
  SELECT id, status, updated_at, NOW() - updated_at AS duration
  FROM analyses
  WHERE status IN ('QUEUED', 'PROCESSING')
    AND updated_at < NOW() - INTERVAL '15 minutes';"
```

---

## 4. Closed Beta Exit Criteria

To graduate from the closed beta phase to general availability (GA) readiness, all criteria below must be satisfied:

1. **48-Hour Soak Success:** Backend ran 48 consecutive hours without crashing, restarting, or triggering Render OOM kill (memory remained < 85%).
2. **Zero Stuck Analyses:** All submitted PCAPs reached terminal state (`COMPLETED` or `FAILED`). The `InspectionReaper` cleaned up any stalled worker threads within 15 minutes.
3. **Connection Pool Hygiene:** HikariCP connection pool remained stable with zero connection acquisition timeouts.
4. **Zero Unhandled 500s:** No internal server exceptions occurred on standard valid PCAP inspections.
5. **Security Verification:** No unauthorized access or data leakage across user workspaces (tenant isolation verified).
6. **Tester Feedback Triaged:** All feedback collected via the feedback channel reviewed and prioritized.

---

## 5. Incident Quick-Reference

If an incident occurs during the closed beta, immediately refer to `docs/RUNBOOK_DISASTER_RECOVERY.md`:

| Incident Type | Emergency Action | Runbook Section |
| :--- | :--- | :--- |
| **Database Corruption or Accidental Drop** | Restore staging database from Supabase automated snapshot. | `docs/RUNBOOK_DISASTER_RECOVERY.md#database-recovery` |
| **Storage Desynchronization** | Run storage reconciliation script to detect orphan/missing S3 captures. | `docs/RUNBOOK_DISASTER_RECOVERY.md#storage-reconciliation` |
| **Worker Queue Lockup** | Inspect stuck threads; restart Render web service via dashboard. | `docs/RUNBOOK_DISASTER_RECOVERY.md#restart-service` |
| **Account Compromise / Token Revocation** | Revoke refresh token families for affected user account. | `docs/RUNBOOK_DISASTER_RECOVERY.md#token-revocation` |
