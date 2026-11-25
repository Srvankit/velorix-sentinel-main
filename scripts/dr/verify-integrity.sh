#!/usr/bin/env bash
# ==============================================================================
# Velorix Sentinel — Integrity Verification Runner (Bash)
# Task 8.3: Post-Restore Database Consistency Check Runner
# ==============================================================================

set -euo pipefail

TARGET_HOST="${DR_TARGET_HOST:-${1:-localhost}}"
TARGET_PORT="${DR_TARGET_PORT:-${2:-5432}}"
TARGET_USER="${DR_TARGET_USER:-${3:-postgres}}"
TARGET_DB="${DR_TARGET_DB:-${4:-postgres}}"
TARGET_PASSWORD="${DR_TARGET_PASSWORD:-${PGPASSWORD:-}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/verify-integrity.sql"

if [[ ! -f "$SQL_FILE" ]]; then
  echo "❌ ERROR: SQL file not found at $SQL_FILE" >&2
  exit 1
fi

echo "=============================================================================="
echo "🛡️  RUNNING POST-RESTORE INTEGRITY VERIFICATION"
echo "=============================================================================="
echo "Target Host: $TARGET_HOST:$TARGET_PORT"
echo "Database:    $TARGET_DB"
echo "User:        $TARGET_USER"
echo "SQL Script:  $SQL_FILE"
echo "------------------------------------------------------------------------------"

PGPASSWORD="$TARGET_PASSWORD" psql \
  --host="$TARGET_HOST" \
  --port="$TARGET_PORT" \
  --username="$TARGET_USER" \
  --dbname="$TARGET_DB" \
  --file="$SQL_FILE"
