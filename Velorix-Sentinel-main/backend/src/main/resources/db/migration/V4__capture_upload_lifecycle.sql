-- Velorix Sentinel :: capture ingestion (V4)
-- Upload lifecycle gains its own status vocabulary, independent from the analysis lifecycle.

ALTER TABLE uploaded_files DROP CONSTRAINT IF EXISTS chk_uploaded_files_status;

UPDATE uploaded_files
SET upload_status = 'READY_FOR_ANALYSIS'
WHERE upload_status IN ('QUEUED', 'PROCESSING', 'COMPLETED');

ALTER TABLE uploaded_files ALTER COLUMN upload_status TYPE VARCHAR(24);

ALTER TABLE uploaded_files
    ADD CONSTRAINT chk_uploaded_files_status CHECK (
        upload_status IN ('UPLOADED', 'VALIDATING', 'READY_FOR_ANALYSIS', 'FAILED', 'DELETED'));

-- A user may not hold two live captures with the same content.
CREATE UNIQUE INDEX IF NOT EXISTS idx_uploaded_files_user_checksum_live
    ON uploaded_files (user_id, checksum)
    WHERE upload_status <> 'DELETED';
