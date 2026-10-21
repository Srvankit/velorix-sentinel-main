-- V8: Case-Insensitive Email Index & Schema Cleanliness (Task 2.2)
--
-- 1. Functional Unique Index on users (LOWER(email)):
--    Standard PostgreSQL unique constraints are case-sensitive (V1:22).
--    The application performs case-insensitive lookups via findByEmailIgnoreCase()
--    and existsByEmailIgnoreCase(), which evaluate LOWER(email) = LOWER(?).
--    This functional unique index guarantees database-level case-insensitive uniqueness
--    and provides direct index support for case-insensitive authentication queries.
--
-- Preflight guarantee: Any existing data must have distinct LOWER(email) values before
-- this migration can succeed.

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_lower_email ON users (LOWER(email));

-- 2. Schema Cleanliness:
--    The detection_rules table was created in V6:45-59, but the platform uses
--    in-memory Java detection rules registered in DetectionRuleRegistry and
--    configured via application.yml (detection.disabled-rules).
--    No JPA entity or Spring Data repository is mapped to detection_rules.
--    Dropping this unmapped table eliminates schema drift (Audit Finding L6).

DROP TABLE IF EXISTS detection_rules;
