#!/usr/bin/env bash
# ==============================================================================
# Velorix Sentinel — Database Backup Script (PostgreSQL / Supabase)
# Task 8.3: Disaster Recovery Database Dump Utility
#
# USAGE:
#   export DR_DB_HOST="aws-0-ap-southeast-1.pooler.supabase.com"
#   export DR_DB_PORT=5432
#   export DR_DB_NAME="postgres"
#   export DR_DB_USER="postgres.<project-ref>"
#   export DR_DB_PASSWORD="<password>"
#   ./scripts/dr/backup-db.sh
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# 1. Configuration & Parameter Resolution
# ------------------------------------------------------------------------------
HOST="${DR_DB_HOST:-${SUPABASE_DB_HOST:-}}"
PORT="${DR_DB_PORT:-${SUPABASE_DB_PORT:-5432}}"
DBNAME="${DR_DB_NAME:-${SUPABASE_DB_NAME:-postgres}}"
USER="${DR_DB_USER:-${SUPABASE_DB_USERNAME:-}}"
PASSWORD="${DR_DB_PASSWORD:-${PGPASSWORD:-${SUPABASE_DB_PASSWORD:-}}}"
BACKUP_DIR="${DR_BACKUP_DIR:-backups}"

# Fail-fast validation
if [[ -z "$HOST" ]]; then
  echo "❌ ERROR: DR_DB_HOST is required. Set DR_DB_HOST or SUPABASE_DB_HOST." >&2
  exit 1
fi

if [[ -z "$USER" ]]; then
  echo "❌ ERROR: DR_DB_USER is required. Set DR_DB_USER or SUPABASE_DB_USERNAME." >&2
  exit 1
fi

if [[ -z "$PASSWORD" ]]; then
  echo "❌ ERROR: Database password is required. Set DR_DB_PASSWORD or PGPASSWORD." >&2
  exit 1
fi

# Ensure output directory exists
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUTPUT_FILE="${BACKUP_DIR}/velorix_db_${TIMESTAMP}.dump"

echo "=============================================================================="
echo "🛡️  VELORIX SENTINEL — DATABASE BACKUP (pg_dump)"
echo "=============================================================================="
echo "Host:         $HOST"
echo "Port:         $PORT"
echo "Database:     $DBNAME"
echo "User:         $USER"
echo "Output Path:  $OUTPUT_FILE"
echo "Format:       PostgreSQL Custom Format (-Fc, public schema, --no-owner, --no-privileges)"
echo "Started At:   $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo "------------------------------------------------------------------------------"

# Note on Supabase Pooler:
# If host contains "pooler.supabase.com", confirm session pooler port 5432 is used.
if [[ "$HOST" =~ "pooler.supabase.com" && "$PORT" != "5432" ]]; then
  echo "⚠️ WARNING: Supabase pooler host detected with port $PORT. Ensure session pooler (port 5432) is used."
fi

# ------------------------------------------------------------------------------
# 2. Execute pg_dump
# ------------------------------------------------------------------------------
START_SECONDS=$SECONDS

# Export PGPASSWORD safely into the subprocess environment only
PGPASSWORD="$PASSWORD" pg_dump \
  --host="$HOST" \
  --port="$PORT" \
  --username="$USER" \
  --dbname="$DBNAME" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --schema=public \
  --file="$OUTPUT_FILE"

ELAPSED=$(( SECONDS - START_SECONDS ))

# ------------------------------------------------------------------------------
# 3. Output Verification & Summary
# ------------------------------------------------------------------------------
if [[ -f "$OUTPUT_FILE" ]]; then
  # Cross-platform stat for bytes
  if stat -c %s "$OUTPUT_FILE" >/dev/null 2>&1; then
    FILE_SIZE_BYTES=$(stat -c %s "$OUTPUT_FILE")
  else
    FILE_SIZE_BYTES=$(stat -f %z "$OUTPUT_FILE")
  fi
  
  FILE_SIZE_MB=$(awk "BEGIN {printf \"%.2f\", $FILE_SIZE_BYTES / (1024 * 1024)}")

  echo "------------------------------------------------------------------------------"
  echo "✅ BACKUP COMPLETED SUCCESSFULLY"
  echo "Dump File:     $OUTPUT_FILE"
  echo "Size:          $FILE_SIZE_BYTES bytes (~${FILE_SIZE_MB} MB)"
  echo "Duration:      ${ELAPSED}s"
  echo "Completed At:  $(date -u +"%Y-%m-%d %H:%M:%SZ")"
  echo "=============================================================================="
else
  echo "❌ ERROR: Backup file was not generated!" >&2
  exit 1
fi
