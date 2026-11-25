# Velorix Sentinel — Disaster Recovery Drill Execution Log

> **Purpose:** Authoritative operational template and record for conducting disaster recovery and restore drills.  
> **Procedure Reference:** [`RUNBOOK_DISASTER_RECOVERY.md`](file:///RUNBOOK_DISASTER_RECOVERY.md)  
> **Target Objectives:** RTO `< 30 minutes`, RPO `< 24 hours` (or `< 1 minute` with PITR)

---

## 1. Drill Metadata & Environmental Parameters

| Parameter | Drill Value / Target | Notes |
| :--- | :--- | :--- |
| **Drill Identifier** | `DR-DRILL-YYYYMMDD-01` | Sequential drill run identifier |
| **Date & Execution Window** | `YYYY-MM-DD` (`HH:MM UTC` to `HH:MM UTC`) | Scheduled drill window |
| **Drill Lead / Incident Commander** | `[Operator Name / Role]` | Primary engineer conducting drill |
| **Peer Verifier / Security Lead** | `[Verifier Name / Role]` | Independent reviewer signing off |
| **Disaster Scenario Simulated** | `[Scenario A / B / C / D / E / F / G / H / I]` | Select from Runbook Section 4 |
| **Source Environment** | `[Production / Staging / Local Snapshot]` | Database and bucket being recovered |
| **Recovery Target Environment** | `[Isolated Rehearsal Instance / New Supabase Project]` | Must NOT overwrite live production |
| **Supabase Plan Tier** | `[Free / Pro / Team / Enterprise]` | *Verify active features in dashboard* |
| **PITR Add-on Active?** | `[YES / NO / N/A]` | Informs expected RPO |

---

## 2. Pre-Filled Sequential Drill Steps & Timing Ledger

Record exact timestamps at each execution boundary:

| Step # | Action / Runbook Phase | Command / Action Executed | Target Threshold | Start Time (UTC) | End Time (UTC) | Elapsed (s) | Status (PASS/FAIL) |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | **Declare Drill & State Freeze** | Announce drill in engineering channel; note initial state | `< 2 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **2** | **Source Database Snapshot** | Execute `backup-db.sh` or `.ps1` via Session Pooler | `< 5 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **3** | **Record Dump Metadata** | Capture file path, SHA-256 checksum, and size in MB | `< 1 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **4** | **Provision Isolated Target** | Launch local PostgreSQL 16 container or test project | `< 5 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **5** | **Test Target Refusal Guard** | Verify `restore-db` refuses remote host without flag | `< 1 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **6** | **Execute Database Restore** | Execute `restore-db.sh` or `.ps1` into target | `< 10 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **7** | **Post-Restore Integrity SQL** | Execute `verify-integrity.sql` via runner | `< 2 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **8** | **Storage S3 Reconciliation** | Execute `reconcile-storage.ts` or runner | `< 3 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **9** | **Actuator & API Smoke Tests** | Probe `/actuator/health`, test login, test upload | `< 3 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **10** | **Session Revocation Test** | Execute `refresh_tokens` mass revocation query | `< 1 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |
| **11** | **Target Teardown / Cleanup** | Destroy isolated drill container / drop drill DB | `< 2 min` | `HH:MM:SS` | `HH:MM:SS` | | `[ ]` |

---

## 3. Quantitative Measurements & Results

### 3.1 Measured Recovery Metrics
- **Total Measured RTO (Step 1 start to Step 9 end):** `XX minutes` *(Target: < 30 minutes)* &rarr; `[MET / BREACHED]`
- **Total Measured RPO (Snapshot timestamp vs simulated failure):** `XX minutes` *(Target: < 24 hours)* &rarr; `[MET / BREACHED]`

### 3.2 Dump & Storage Assets
- **Dump File Name:** `velorix_db_YYYYMMDD_HHMMSS.dump`
- **Dump File Size:** `XXX,XXX bytes (~XX.XX MB)`
- **Dump Generation Duration:** `XX seconds`
- **Database Restore Duration:** `XX seconds`

### 3.3 Database Integrity Audit Output
- **Total Tables Restored:** `11 / 11 public tables`
- **Flyway Migrations Count:** `12 / 12 migrations (V1 to V12)`
- **Flyway Success Count:** `12 (0 failed rows)`
- **Foreign Key Orphans Detected:** `0 (packets, threats, summaries, files, tokens, codes)`
- **Duplicate Lowercase Emails:** `0 duplicate accounts`
- **Tables with RLS Enabled:** `11 / 11 public tables`

### 3.4 Object Storage Reconciliation Summary
- **Database File Records:** `XX captures`
- **Physical S3 Objects Found:** `XX objects`
- **Matched Captures:** `XX`
- **Missing Captures (Critical):** `0`
- **Orphaned S3 Objects:** `0`

---

## 4. Anomalies, Deficiencies & Corrective Action Items

Record any operational friction, unexpected errors, or missing tooling discovered during the drill:

| # | Observation / Issue Description | Severity | Proposed Remediation | Assignee | Due Date |
| :-: | :--- | :---: | :--- | :--- | :--- |
| **1** | *[Example: IPv6 timeout on direct host]* | Low | *Confirmed session pooler host must be used in runbook.* | *[DevOps]* | *Done* |
| **2** | | | | | |

---

## 5. Formal Drill Sign-Off & Verification

We certify that the disaster recovery drill described above was executed following standard operational procedures, that the recovery objectives were measured and evaluated honestly, and that all post-restore integrity and security checks passed without data corruption.

- **Drill Operator Signature:** ___________________________ (`Date: YYYY-MM-DD`)
- **Reviewer / Security Officer:** ___________________________ (`Date: YYYY-MM-DD`)
