-- V7: Supabase Row Level Security (RLS) & Authorization Hardening
--
-- Security Context & Architecture:
-- 1. By default on Supabase, the PostgREST Data API exposes all tables in the public schema
--    to HTTP clients presenting the project's public "anon" or "authenticated" JWT tokens.
-- 2. Velorix Sentinel is an enterprise network security platform where all end-user interactions
--    MUST flow through the Spring Boot application tier (/api/v1/**). No client is permitted
--    to query or modify PostgreSQL directly via PostgREST.
-- 3. Enabling Row Level Security (RLS) on each public table with zero permissive policies creates
--    a default-deny perimeter against PostgREST (returning empty results / 401 Unauthorized).
-- 4. In Supabase PostgreSQL, the backend connects using administrative / table-owning credentials
--    (e.g., role "postgres"). Table owners and superusers bypass RLS by default in PostgreSQL.
--    Therefore, RLS serves as defense-in-depth against direct Data API abuse, while application-level
--    ownership checks (via CurrentUserProvider.ownerScope()) remain mandatory and authoritative.
-- 5. Privileges on schema public are explicitly revoked from "anon" and "authenticated" roles.
--    This logic is conditionally wrapped in a PL/pgSQL block to ensure safe execution in standard
--    PostgreSQL environments where Supabase-specific roles do not exist.

-- ============================================================================
-- 1. Enable Row Level Security (RLS) on all public application tables
-- ============================================================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE uploaded_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE analyses ENABLE ROW LEVEL SECURITY;
ALTER TABLE packets ENABLE ROW LEVEL SECURITY;
ALTER TABLE threats ENABLE ROW LEVEL SECURITY;
ALTER TABLE analysis_summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE detection_rules ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 2. Revoke all privileges from Supabase PostgREST roles (anon, authenticated)
-- ============================================================================

DO $$
BEGIN
    -- Revoke from 'anon' (unauthenticated PostgREST requests)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;
        REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon;

        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM anon;
    END IF;

    -- Revoke from 'authenticated' (direct Supabase-authenticated PostgREST requests)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated;
        REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM authenticated;

        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM authenticated;
    END IF;
END $$;
