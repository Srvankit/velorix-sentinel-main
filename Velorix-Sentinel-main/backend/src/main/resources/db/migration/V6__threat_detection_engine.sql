-- V6: Threat detection engine.
--
-- Widens the threat row from "a label" into a full finding: which rule fired, on which
-- hosts, with what evidence and which frames justify it. Detection is re-runnable, so the
-- table is treated as derived data owned by one analysis.

ALTER TABLE threats
    ALTER COLUMN threat_type TYPE VARCHAR(60);

ALTER TABLE threats
    ADD COLUMN IF NOT EXISTS detection_rule        VARCHAR(60)   NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS rule_version          VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    ADD COLUMN IF NOT EXISTS evidence              VARCHAR(4000) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS mitre_technique       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS mitre_technique_name  VARCHAR(120),
    ADD COLUMN IF NOT EXISTS source_ip             VARCHAR(45),
    ADD COLUMN IF NOT EXISTS destination_ip        VARCHAR(45),
    ADD COLUMN IF NOT EXISTS protocol              VARCHAR(10)   NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS packet_count          BIGINT        NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sample_packet_numbers VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS first_seen_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_seen_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status                VARCHAR(20)   NOT NULL DEFAULT 'OPEN';

ALTER TABLE threats
    DROP CONSTRAINT IF EXISTS chk_threats_status;
ALTER TABLE threats
    ADD CONSTRAINT chk_threats_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'FALSE_POSITIVE'));

ALTER TABLE threats
    DROP CONSTRAINT IF EXISTS chk_threats_packet_count;
ALTER TABLE threats
    ADD CONSTRAINT chk_threats_packet_count CHECK (packet_count >= 0);

-- The threat feed is read as "every finding of one analysis, worst first", and grouped by
-- rule, category and host for the summary endpoint.
CREATE INDEX IF NOT EXISTS idx_threats_analysis_severity ON threats (analysis_id, severity);
CREATE INDEX IF NOT EXISTS idx_threats_rule ON threats (detection_rule);
CREATE INDEX IF NOT EXISTS idx_threats_source_ip ON threats (source_ip);
CREATE INDEX IF NOT EXISTS idx_threats_destination_ip ON threats (destination_ip);

-- Detection rule catalogue. Rules are code, but their enablement and metadata are data so
-- an operator can silence a noisy rule without a redeploy.
CREATE TABLE IF NOT EXISTS detection_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id     VARCHAR(60)   NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category    VARCHAR(60)   NOT NULL,
    version     VARCHAR(20)   NOT NULL,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version_no  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_detection_rules_rule_id UNIQUE (rule_id)
);

CREATE INDEX IF NOT EXISTS idx_detection_rules_enabled ON detection_rules (enabled);
