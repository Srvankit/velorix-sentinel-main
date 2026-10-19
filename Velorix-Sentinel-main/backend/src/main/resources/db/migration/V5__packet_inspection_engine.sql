-- V5: deep packet inspection engine.
-- Adds decoded protocol metadata to packets and pipeline progress + capture level
-- aggregates to analyses.

ALTER TABLE packets
    ADD COLUMN IF NOT EXISTS network_protocol       VARCHAR(10) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS source_mac             VARCHAR(17),
    ADD COLUMN IF NOT EXISTS destination_mac        VARCHAR(17),
    ADD COLUMN IF NOT EXISTS captured_length        INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ttl                    INTEGER,
    ADD COLUMN IF NOT EXISTS sequence_number        BIGINT,
    ADD COLUMN IF NOT EXISTS acknowledgement_number BIGINT,
    ADD COLUMN IF NOT EXISTS window_size            INTEGER,
    ADD COLUMN IF NOT EXISTS checksum               INTEGER,
    ADD COLUMN IF NOT EXISTS info                   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_malformed           BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE packets
    DROP CONSTRAINT IF EXISTS chk_packets_network_protocol;
ALTER TABLE packets
    ADD CONSTRAINT chk_packets_network_protocol CHECK (network_protocol IN ('IPV4', 'IPV6', 'ARP', 'OTHER'));

ALTER TABLE packets
    DROP CONSTRAINT IF EXISTS chk_packets_ttl;
ALTER TABLE packets
    ADD CONSTRAINT chk_packets_ttl CHECK (ttl IS NULL OR ttl BETWEEN 0 AND 255);

ALTER TABLE packets
    DROP CONSTRAINT IF EXISTS chk_packets_ports;
ALTER TABLE packets
    ADD CONSTRAINT chk_packets_ports CHECK (
        (source_port IS NULL OR source_port BETWEEN 0 AND 65535)
            AND (destination_port IS NULL OR destination_port BETWEEN 0 AND 65535));

-- The packet table is read almost exclusively as "one page of one analysis, ordered by
-- packet number", and aggregated as "protocol histogram of one analysis".
CREATE INDEX IF NOT EXISTS idx_packets_analysis_number ON packets (analysis_id, packet_number);
CREATE INDEX IF NOT EXISTS idx_packets_analysis_protocol ON packets (analysis_id, protocol);
CREATE INDEX IF NOT EXISTS idx_packets_analysis_timestamp ON packets (analysis_id, packet_timestamp);

ALTER TABLE analyses
    ADD COLUMN IF NOT EXISTS stage                  VARCHAR(20)      NOT NULL DEFAULT 'PREPARING',
    ADD COLUMN IF NOT EXISTS processed_packets      BIGINT           NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS progress_percent       INTEGER          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS malformed_packets      BIGINT           NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_truncated           BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS capture_started_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS capture_ended_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS capture_duration_ms    BIGINT,
    ADD COLUMN IF NOT EXISTS average_packet_size    DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unique_source_ips      BIGINT           NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unique_destination_ips BIGINT           NOT NULL DEFAULT 0;

ALTER TABLE analyses
    DROP CONSTRAINT IF EXISTS chk_analyses_stage;
ALTER TABLE analyses
    ADD CONSTRAINT chk_analyses_stage CHECK (
        stage IN ('PREPARING', 'READING', 'EXTRACTING', 'SAVING', 'COMPLETED', 'FAILED'));

ALTER TABLE analyses
    DROP CONSTRAINT IF EXISTS chk_analyses_progress;
ALTER TABLE analyses
    ADD CONSTRAINT chk_analyses_progress CHECK (progress_percent BETWEEN 0 AND 100);

-- Only one live inspection run may exist per capture; finished runs are kept as history.
CREATE UNIQUE INDEX IF NOT EXISTS idx_analyses_active_per_file
    ON analyses (uploaded_file_id)
    WHERE status IN ('QUEUED', 'PROCESSING');
