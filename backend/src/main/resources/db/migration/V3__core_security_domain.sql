-- Velorix Sentinel :: core security domain (V3)
-- Uploaded captures, analyses, packets, threats and pre-aggregated summaries.

CREATE TABLE IF NOT EXISTS uploaded_files (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255) NOT NULL,
    file_size          BIGINT       NOT NULL,
    file_type          VARCHAR(100) NOT NULL,
    upload_status      VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    checksum           VARCHAR(64)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_uploaded_files_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_uploaded_files_status CHECK (
        upload_status IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_uploaded_files_size CHECK (file_size >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_uploaded_files_stored_name ON uploaded_files (stored_file_name);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_user ON uploaded_files (user_id);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_status ON uploaded_files (upload_status);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_created_at ON uploaded_files (created_at);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_checksum ON uploaded_files (checksum);

CREATE TABLE IF NOT EXISTS analyses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_file_id  UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    duration_ms       BIGINT,
    total_packets     BIGINT      NOT NULL DEFAULT 0,
    malicious_packets BIGINT      NOT NULL DEFAULT 0,
    safe_packets      BIGINT      NOT NULL DEFAULT 0,
    risk_score        INTEGER     NOT NULL DEFAULT 0,
    analysis_version  VARCHAR(20) NOT NULL,
    failure_reason    VARCHAR(512),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_analyses_uploaded_file FOREIGN KEY (uploaded_file_id)
        REFERENCES uploaded_files (id) ON DELETE CASCADE,
    CONSTRAINT chk_analyses_status CHECK (
        status IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_analyses_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_analyses_file ON analyses (uploaded_file_id);
CREATE INDEX IF NOT EXISTS idx_analyses_status ON analyses (status);
CREATE INDEX IF NOT EXISTS idx_analyses_created_at ON analyses (created_at);

CREATE TABLE IF NOT EXISTS packets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id      UUID        NOT NULL,
    packet_number    BIGINT      NOT NULL,
    packet_timestamp TIMESTAMPTZ NOT NULL,
    source_ip        VARCHAR(45) NOT NULL,
    destination_ip   VARCHAR(45) NOT NULL,
    source_port      INTEGER,
    destination_port INTEGER,
    protocol         VARCHAR(10) NOT NULL DEFAULT 'OTHER',
    packet_length    INTEGER     NOT NULL,
    tcp_flags        VARCHAR(40),
    payload_size     INTEGER     NOT NULL DEFAULT 0,
    is_suspicious    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_packets_analysis FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE,
    CONSTRAINT uk_packets_analysis_number UNIQUE (analysis_id, packet_number),
    CONSTRAINT chk_packets_ports CHECK (
        (source_port IS NULL OR source_port BETWEEN 0 AND 65535)
        AND (destination_port IS NULL OR destination_port BETWEEN 0 AND 65535))
);

CREATE INDEX IF NOT EXISTS idx_packets_analysis ON packets (analysis_id);
CREATE INDEX IF NOT EXISTS idx_packets_source_ip ON packets (source_ip);
CREATE INDEX IF NOT EXISTS idx_packets_destination_ip ON packets (destination_ip);
CREATE INDEX IF NOT EXISTS idx_packets_protocol ON packets (protocol);
CREATE INDEX IF NOT EXISTS idx_packets_suspicious ON packets (is_suspicious);

CREATE TABLE IF NOT EXISTS threats (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id      UUID          NOT NULL,
    threat_type      VARCHAR(30)   NOT NULL DEFAULT 'UNKNOWN',
    severity         VARCHAR(10)   NOT NULL DEFAULT 'LOW',
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    title            VARCHAR(160)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    recommendation   VARCHAR(2000),
    detected_at      TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_threats_analysis FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE,
    CONSTRAINT chk_threats_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_threats_confidence CHECK (confidence_score BETWEEN 0 AND 1)
);

CREATE INDEX IF NOT EXISTS idx_threats_analysis ON threats (analysis_id);
CREATE INDEX IF NOT EXISTS idx_threats_type ON threats (threat_type);
CREATE INDEX IF NOT EXISTS idx_threats_severity ON threats (severity);
CREATE INDEX IF NOT EXISTS idx_threats_detected_at ON threats (detected_at);

CREATE TABLE IF NOT EXISTS analysis_summaries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id      UUID        NOT NULL,
    total_threats    BIGINT      NOT NULL DEFAULT 0,
    critical_threats BIGINT      NOT NULL DEFAULT 0,
    high_threats     BIGINT      NOT NULL DEFAULT 0,
    medium_threats   BIGINT      NOT NULL DEFAULT 0,
    low_threats      BIGINT      NOT NULL DEFAULT 0,
    risk_score       INTEGER     NOT NULL DEFAULT 0,
    overall_status   VARCHAR(20) NOT NULL DEFAULT 'SECURE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_analysis_summaries_analysis FOREIGN KEY (analysis_id)
        REFERENCES analyses (id) ON DELETE CASCADE,
    CONSTRAINT chk_analysis_summaries_status CHECK (
        overall_status IN ('SECURE', 'LOW_RISK', 'ELEVATED', 'HIGH_RISK', 'CRITICAL')),
    CONSTRAINT chk_analysis_summaries_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_analysis_summaries_analysis ON analysis_summaries (analysis_id);
CREATE INDEX IF NOT EXISTS idx_analysis_summaries_status ON analysis_summaries (overall_status);
