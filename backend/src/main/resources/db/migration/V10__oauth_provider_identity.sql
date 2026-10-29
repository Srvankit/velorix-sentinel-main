-- V10: OAuth Provider Identity & Account Linking (Task 3.3)
--
-- Adds explicit OAuth provider tracking columns to users table and enforces
-- uniqueness on (oauth_provider, oauth_provider_id) to defend against pre-account
-- takeover and prevent identity collisions.

ALTER TABLE users ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_oauth_provider_id
    ON users (oauth_provider, oauth_provider_id);
