-- V12: Refresh Token Family Tracking & Reuse Detection (Task 3.6)
--
-- Adds family_id and previous_token_hash to refresh_tokens table to enable
-- token family rotation tracking, pessimistic concurrency locking, and
-- automatic revocation of compromised token families upon reuse replay.

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS family_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS previous_token_hash VARCHAR(128);

-- Backfill existing refresh tokens with their own unique family_id
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

-- Enforce NOT NULL on family_id after backfill
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_previous_hash ON refresh_tokens (previous_token_hash);

