-- ==============================================================================
-- Velorix Sentinel — Database Disaster Recovery Integrity Verification Script
-- Task 8.3: Post-Restore Integrity, Foreign Key Consistency & RLS Validation
--
-- USAGE:
--   psql -h <HOST> -p <PORT> -U <USER> -d <DB> -f scripts/dr/verify-integrity.sql
-- ==============================================================================

\set ON_ERROR_STOP on
\pset null '<NULL>'

\echo ''
\echo '=============================================================================='
\echo '🛡️  VELORIX SENTINEL — POST-RESTORE INTEGRITY VERIFICATION'
\echo '=============================================================================='
\echo ''

-- ------------------------------------------------------------------------------
-- 1. Table Row Counts (All 11 Public Tables)
-- ------------------------------------------------------------------------------
\echo '>>> 1. TABLE ROW COUNTS'

SELECT 
    schemaname || '.' || relname AS table_name,
    n_live_tup AS estimated_row_count
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY relname;

-- ------------------------------------------------------------------------------
-- 2. Flyway Schema History State (V1 through V12)
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 2. FLYWAY SCHEMA MIGRATION STATE'

SELECT 
    installed_rank,
    version,
    description,
    script,
    installed_on,
    execution_time AS exec_time_ms,
    success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Assert Flyway migration health:
-- Expected: Exactly 12 applied migrations (V1 to V12), all success = TRUE, 0 failures.
DO $$
DECLARE
    v_total_migrations INT;
    v_successful_migrations INT;
    v_failed_migrations INT;
    v_latest_version VARCHAR;
BEGIN
    SELECT COUNT(*), 
           COUNT(*) FILTER (WHERE success = TRUE),
           COUNT(*) FILTER (WHERE success = FALSE)
    INTO v_total_migrations, v_successful_migrations, v_failed_migrations
    FROM flyway_schema_history;

    SELECT version INTO v_latest_version 
    FROM flyway_schema_history 
    ORDER BY installed_rank DESC LIMIT 1;

    RAISE NOTICE 'Flyway Migration Audit: % total, % successful, % failed, latest version: %',
        v_total_migrations, v_successful_migrations, v_failed_migrations, v_latest_version;

    IF v_failed_migrations > 0 THEN
        RAISE EXCEPTION 'CRITICAL INTEGRITY FAILURE: % failed Flyway migration(s) detected in flyway_schema_history!', v_failed_migrations;
    END IF;

    IF v_total_migrations < 12 THEN
        RAISE WARNING 'INCOMPLETE MIGRATION SET: Found % migrations, expected 12 (V1 through V12).', v_total_migrations;
    ELSE
        RAISE NOTICE 'SUCCESS: All 12 Flyway schema migrations verified.';
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 3. Foreign Key Orphan Integrity Checks
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 3. FOREIGN KEY ORPHAN AUDIT'

-- 3a. Packets orphaned from analyses
SELECT COUNT(*) AS orphan_packets_count
FROM packets p
LEFT JOIN analyses a ON p.analysis_id = a.id
WHERE a.id IS NULL;

-- 3b. Threats orphaned from analyses
SELECT COUNT(*) AS orphan_threats_count
FROM threats t
LEFT JOIN analyses a ON t.analysis_id = a.id
WHERE a.id IS NULL;

-- 3c. Analysis summaries orphaned from analyses
SELECT COUNT(*) AS orphan_analysis_summaries_count
FROM analysis_summaries s
LEFT JOIN analyses a ON s.analysis_id = a.id
WHERE a.id IS NULL;

-- 3d. Analyses orphaned from uploaded_files
SELECT COUNT(*) AS orphan_analyses_count
FROM analyses a
LEFT JOIN uploaded_files u ON a.uploaded_file_id = u.id
WHERE u.id IS NULL;

-- 3e. Refresh tokens orphaned from users
SELECT COUNT(*) AS orphan_refresh_tokens_count
FROM refresh_tokens rt
LEFT JOIN users u ON rt.user_id = u.id
WHERE u.id IS NULL;

-- 3f. Verification tokens orphaned from users
SELECT COUNT(*) AS orphan_verification_tokens_count
FROM verification_tokens vt
LEFT JOIN users u ON vt.user_id = u.id
WHERE u.id IS NULL;

-- 3g. OAuth exchange codes orphaned from users
SELECT COUNT(*) AS orphan_oauth_codes_count
FROM oauth_exchange_codes oec
LEFT JOIN users u ON oec.user_id = u.id
WHERE u.id IS NULL;

-- 3h. Uploaded files orphaned from users
SELECT COUNT(*) AS orphan_uploaded_files_count
FROM uploaded_files uf
LEFT JOIN users u ON uf.user_id = u.id
WHERE u.id IS NULL;

-- Assert zero orphans across all relational hierarchies
DO $$
DECLARE
    v_orphans INT := 0;
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM packets p LEFT JOIN analyses a ON p.analysis_id = a.id WHERE a.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM threats t LEFT JOIN analyses a ON t.analysis_id = a.id WHERE a.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM analysis_summaries s LEFT JOIN analyses a ON s.analysis_id = a.id WHERE a.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM analyses a LEFT JOIN uploaded_files u ON a.uploaded_file_id = u.id WHERE u.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM refresh_tokens rt LEFT JOIN users u ON rt.user_id = u.id WHERE u.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM verification_tokens vt LEFT JOIN users u ON vt.user_id = u.id WHERE u.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM oauth_exchange_codes oec LEFT JOIN users u ON oec.user_id = u.id WHERE u.id IS NULL;
    v_orphans := v_orphans + v_cnt;
    SELECT COUNT(*) INTO v_cnt FROM uploaded_files uf LEFT JOIN users u ON uf.user_id = u.id WHERE u.id IS NULL;
    v_orphans := v_orphans + v_cnt;

    IF v_orphans > 0 THEN
        RAISE EXCEPTION 'CRITICAL INTEGRITY FAILURE: Found % orphaned records violating relational constraints!', v_orphans;
    ELSE
        RAISE NOTICE 'SUCCESS: Zero foreign key orphan records found across all tables.';
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 4. Duplicate Case-Insensitive Email Audit
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 4. DUPLICATE EMAIL AUDIT (CASE-INSENSITIVE lower(email))'

SELECT 
    LOWER(email) AS normalized_email,
    COUNT(*) AS account_count,
    ARRAY_AGG(id::text) AS user_ids
FROM users
GROUP BY LOWER(email)
HAVING COUNT(*) > 1;

DO $$
DECLARE
    v_duplicate_emails INT;
BEGIN
    SELECT COUNT(*) INTO v_duplicate_emails
    FROM (
        SELECT LOWER(email) FROM users GROUP BY LOWER(email) HAVING COUNT(*) > 1
    ) sub;

    IF v_duplicate_emails > 0 THEN
        RAISE EXCEPTION 'CRITICAL INTEGRITY FAILURE: % duplicate case-insensitive email accounts exist!', v_duplicate_emails;
    ELSE
        RAISE NOTICE 'SUCCESS: All user email addresses are strictly unique case-insensitively.';
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 5. Zombie / Stuck Analysis Runs (> 30 minutes in QUEUED or PROCESSING)
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 5. ZOMBIE / STUCK ANALYSIS AUDIT'

SELECT 
    id,
    uploaded_file_id,
    status,
    started_at,
    created_at,
    NOW() - created_at AS elapsed_since_creation
FROM analyses
WHERE status IN ('QUEUED', 'PROCESSING')
  AND created_at < (NOW() - INTERVAL '30 minutes')
ORDER BY created_at ASC;

-- ------------------------------------------------------------------------------
-- 6. Uploaded Files Consistency Check
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 6. UPLOADED CAPTURE DATABASE METRICS'

SELECT 
    upload_status,
    COUNT(*) AS file_count,
    COALESCE(SUM(file_size), 0) AS total_bytes,
    ROUND(COALESCE(SUM(file_size), 0) / (1024.0 * 1024.0), 2) AS total_mb
FROM uploaded_files
GROUP BY upload_status
ORDER BY upload_status;

\echo ''
\echo 'Note: To verify physical object existence in S3 storage, run scripts/dr/reconcile-storage.ts'

-- ------------------------------------------------------------------------------
-- 7. Row Level Security (RLS) State on All Public Tables
-- ------------------------------------------------------------------------------
\echo ''
\echo '>>> 7. ROW LEVEL SECURITY (RLS) STATUS AUDIT'

SELECT 
    tablename,
    rowsecurity AS rls_enabled
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;

DO $$
DECLARE
    v_unprotected_tables INT;
    v_unprotected_names TEXT;
BEGIN
    SELECT COUNT(*), STRING_AGG(tablename, ', ')
    INTO v_unprotected_tables, v_unprotected_names
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename NOT IN ('flyway_schema_history')
      AND rowsecurity = FALSE;

    IF v_unprotected_tables > 0 THEN
        RAISE EXCEPTION 'SECURITY AUDIT FAILURE: % table(s) lack Row Level Security: %', v_unprotected_tables, v_unprotected_names;
    ELSE
        RAISE NOTICE 'SUCCESS: Row Level Security is enabled on every public application table.';
    END IF;
END $$;

\echo ''
\echo 'RLS INTERPRETATION NOTE:'
\echo '  - On Supabase Production: RLS protects tables from direct PostgREST (anon/authenticated) access.'
\echo '  - On Local/Isolated Restore: RLS status shows TRUE on table definitions, but Supabase-specific'
\echo '    roles (anon/authenticated) will not exist after a --no-privileges restore. This is expected'
\echo '    and normal for local disaster recovery rehearsals.'
\echo ''
\echo '=============================================================================='
\echo '✅ DATABASE POST-RESTORE INTEGRITY VERIFICATION COMPLETED SUCCESSFULLY'
\echo '=============================================================================='
\echo ''
