-- V11: OAuth One-Time Exchange Codes (Task 3.5)
--
-- Adds table for short-lived (60s), single-use OAuth authorization codes.
-- Codes are stored hashed with SHA-256; raw codes are never persisted.
-- Row Level Security (RLS) is enabled and permissions revoked from PostgREST API roles.

CREATE TABLE IF NOT EXISTS oauth_exchange_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_oauth_exchange_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_oauth_exchange_codes_hash ON oauth_exchange_codes (code_hash);
CREATE INDEX IF NOT EXISTS idx_oauth_exchange_codes_user ON oauth_exchange_codes (user_id);
CREATE INDEX IF NOT EXISTS idx_oauth_exchange_codes_expires ON oauth_exchange_codes (expires_at);

-- Hardened for Supabase: enable RLS and revoke access from PostgREST API roles
ALTER TABLE oauth_exchange_codes ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE oauth_exchange_codes FROM anon;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE oauth_exchange_codes FROM authenticated;
    END IF;
END $$;
