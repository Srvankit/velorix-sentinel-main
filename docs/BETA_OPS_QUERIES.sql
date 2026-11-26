-- ==============================================================================
-- VELORIX SENTINEL — CLOSED BETA OPERATIONAL TELEMETRY & AUDIT QUERIES
-- ==============================================================================
-- STATUS: UNVERIFIED - needs first real run against live staging database.
-- (Local environment does not have a live PostgreSQL database running).
--
-- PURPOSE:
-- 10 read-only operational queries for solo-owner monitoring, capacity planning,
-- and triage during the Velorix Sentinel closed beta.
--
-- SECURITY & COMPLIANCE:
-- - All queries are strictly read-only SELECT statements.
-- - No secrets, raw passwords, or decrypted session tokens are accessed.
-- - Table and column names are strictly verified against Flyway migrations V1-V12.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- QUERY 1: Stuck Analyses Beyond Reaper Timeout (15 minutes)
-- ------------------------------------------------------------------------------
-- Identifies analyses remaining in QUEUED or PROCESSING longer than the 15-minute
-- inspection timeout (INSPECTION_MAX_DURATION / properties.maxDuration()).
-- These indicate tasks where worker threads crashed, died, or failed to release.
SELECT
    a.id AS analysis_id,
    a.uploaded_file_id,
    f.original_file_name,
    f.file_size AS file_size_bytes,
    a.status,
    a.started_at,
    a.updated_at,
    NOW() - a.updated_at AS time_in_current_state
FROM analyses a
JOIN uploaded_files f ON a.uploaded_file_id = f.id
WHERE a.status IN ('QUEUED', 'PROCESSING')
  AND a.updated_at < NOW() - INTERVAL '15 minutes'
ORDER BY a.updated_at ASC;


-- ------------------------------------------------------------------------------
-- QUERY 2: Failed Analyses in the Last 24 Hours and Last 7 Days
-- ------------------------------------------------------------------------------
-- Aggregates failure reasons to distinguish PCAP parsing errors from system timeouts.
SELECT
    COALESCE(a.failure_reason, 'Unspecified failure') AS failure_reason,
    COUNT(*) FILTER (WHERE a.updated_at >= NOW() - INTERVAL '24 hours') AS failures_last_24h,
    COUNT(*) FILTER (WHERE a.updated_at >= NOW() - INTERVAL '7 days') AS failures_last_7d
FROM analyses a
WHERE a.status = 'FAILED'
  AND a.updated_at >= NOW() - INTERVAL '7 days'
GROUP BY COALESCE(a.failure_reason, 'Unspecified failure')
ORDER BY failures_last_24h DESC, failures_last_7d DESC;


-- ------------------------------------------------------------------------------
-- QUERY 3: Analysis Execution Duration Percentiles (p50, p90, p99)
-- ------------------------------------------------------------------------------
-- Measures real inspection performance on completed PCAP captures over the last 7 days.
SELECT
    COUNT(*) AS completed_analyses_count,
    MIN(duration_ms) AS min_duration_ms,
    ROUND(AVG(duration_ms)::numeric, 2) AS avg_duration_ms,
    percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_ms) AS p50_duration_ms,
    percentile_cont(0.90) WITHIN GROUP (ORDER BY duration_ms) AS p90_duration_ms,
    percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_duration_ms,
    MAX(duration_ms) AS max_duration_ms
FROM analyses
WHERE status = 'COMPLETED'
  AND duration_ms IS NOT NULL
  AND completed_at >= NOW() - INTERVAL '7 days';


-- ------------------------------------------------------------------------------
-- QUERY 4: Top Users by Upload Count and Storage vs. Quota
-- ------------------------------------------------------------------------------
-- Tracks storage usage against default beta quotas (10 files / 500MB storage per user).
-- Excludes soft-deleted files if applicable.
SELECT
    u.id AS user_id,
    u.email,
    u.role,
    COUNT(f.id) AS total_files_uploaded,
    10 AS file_quota_limit,
    ROUND(SUM(f.file_size)::numeric / 1048576, 2) AS total_storage_used_mb,
    500.00 AS storage_quota_mb,
    ROUND((SUM(f.file_size)::numeric / 524288000.0) * 100, 1) AS storage_quota_pct_used
FROM users u
JOIN uploaded_files f ON u.id = f.user_id
GROUP BY u.id, u.email, u.role
ORDER BY total_storage_used_mb DESC
LIMIT 25;


-- ------------------------------------------------------------------------------
-- QUERY 5: Failed Sign-in Attempts in Last 24 Hours (Activity Audit)
-- ------------------------------------------------------------------------------
-- Detects credential stuffing, brute-force attempts, or misconfigured API clients.
SELECT
    al.ip_address,
    al.description,
    COUNT(*) AS attempt_count,
    MIN(al.created_at) AS first_attempt_at,
    MAX(al.created_at) AS last_attempt_at
FROM activity_logs al
WHERE al.activity_type = 'LOGIN_FAILURE'
  AND al.created_at >= NOW() - INTERVAL '24 hours'
GROUP BY al.ip_address, al.description
ORDER BY attempt_count DESC
LIMIT 50;


-- ------------------------------------------------------------------------------
-- QUERY 6: Unverified User Accounts Older than 48 Hours
-- ------------------------------------------------------------------------------
-- Identifies inactive pending registrations that may need retention cleanup.
SELECT
    u.id AS user_id,
    u.email,
    u.first_name,
    u.last_name,
    u.created_at,
    NOW() - u.created_at AS pending_age
FROM users u
WHERE u.is_verified = FALSE
  AND u.created_at < NOW() - INTERVAL '48 hours'
ORDER BY u.created_at ASC;


-- ------------------------------------------------------------------------------
-- QUERY 7: Daily User Signups Over the Last 30 Days
-- ------------------------------------------------------------------------------
-- Tracks beta onboarding velocity and cohort growth.
SELECT
    DATE_TRUNC('day', created_at) AS signup_date,
    COUNT(*) AS new_user_count,
    COUNT(*) FILTER (WHERE is_verified = TRUE) AS verified_user_count
FROM users
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at)
ORDER BY signup_date DESC;


-- ------------------------------------------------------------------------------
-- QUERY 8: Daily Upload Velocity & Volume Over Last 30 Days
-- ------------------------------------------------------------------------------
-- Monitors storage ingestion trend and spikes in network capture uploads.
SELECT
    DATE_TRUNC('day', created_at) AS upload_date,
    COUNT(*) AS file_count,
    ROUND(SUM(file_size)::numeric / 1048576, 2) AS total_uploaded_mb,
    ROUND(AVG(file_size)::numeric / 1048576, 2) AS avg_file_size_mb
FROM uploaded_files
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at)
ORDER BY upload_date DESC;


-- ------------------------------------------------------------------------------
-- QUERY 9: Top Threat Findings Grouped by Detection Rule & Severity
-- ------------------------------------------------------------------------------
-- Verifies detection engine efficacy and identifies the most frequent alerts.
SELECT
    t.detection_rule,
    t.severity,
    t.threat_type,
    COUNT(*) AS detection_count,
    ROUND(AVG(t.confidence_score)::numeric, 3) AS avg_confidence_score,
    COUNT(DISTINCT t.analysis_id) AS distinct_analyses_affected
FROM threats t
GROUP BY t.detection_rule, t.severity, t.threat_type
ORDER BY detection_count DESC
LIMIT 30;


-- ------------------------------------------------------------------------------
-- QUERY 10: Truncated, Malformed, or Zero-Packet Network Captures
-- ------------------------------------------------------------------------------
-- Audits captures that yielded 0 packets or failed due to frame corruption.
SELECT
    a.id AS analysis_id,
    f.original_file_name,
    f.file_size AS file_size_bytes,
    a.status,
    a.total_packets,
    a.failure_reason,
    a.created_at
FROM analyses a
JOIN uploaded_files f ON a.uploaded_file_id = f.id
WHERE (a.status = 'COMPLETED' AND a.total_packets = 0)
   OR (a.failure_reason ILIKE '%truncat%'
       OR a.failure_reason ILIKE '%corrupt%'
       OR a.failure_reason ILIKE '%malformed%'
       OR a.failure_reason ILIKE '%pcap%')
ORDER BY a.created_at DESC
LIMIT 50;
