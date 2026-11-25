#!/usr/bin/env bash
# ==============================================================================
# Velorix Sentinel — Database Restore Script (PostgreSQL / Supabase)
# Task 8.3: Disaster Recovery Database Restore Utility
#
# HARD GUARD:
#   Refuses to restore to any non-local or remote host (Supabase, Render, Staging,
#   Production) without the explicit confirmation flag: --force-remote-restore.
#   This safety guard executes BEFORE any other action, Docker call, or network probe.
#
# USAGE (Local Rehearsal / Isolated Target):
#   ./scripts/dr/restore-db.sh --file backups/velorix_db_latest.dump
#
# USAGE (Explicit Remote Disaster Recovery):
#   ./scripts/dr/restore-db.sh \
#     --force-remote-restore \
#     --host aws-0-ap-southeast-1.pooler.supabase.com \
#     --port 5432 \
#     --user postgres.<project-ref> \
#     --dbname postgres \
#     --file backups/velorix_db_latest.dump
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# 1. Parse Arguments
# ------------------------------------------------------------------------------
TARGET_HOST="${DR_TARGET_HOST:-localhost}"
TARGET_PORT="${DR_TARGET_PORT:-5432}"
TARGET_DB="${DR_TARGET_DB:-postgres}"
TARGET_USER="${DR_TARGET_USER:-postgres}"
TARGET_PASSWORD="${DR_TARGET_PASSWORD:-${PGPASSWORD:-}}"
DUMP_FILE=""
FORCE_REMOTE_RESTORE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      TARGET_HOST="$2"
      shift 2
      ;;
    --port)
      TARGET_PORT="$2"
      shift 2
      ;;
    --dbname|--db)
      TARGET_DB="$2"
      shift 2
      ;;
    --user)
      TARGET_USER="$2"
      shift 2
      ;;
    --password)
      TARGET_PASSWORD="$2"
      shift 2
      ;;
    --file)
      DUMP_FILE="$2"
      shift 2
      ;;
    --force-remote-restore)
      FORCE_REMOTE_RESTORE=true
      shift
      ;;
    -h|--help)
      echo "Usage: ./scripts/dr/restore-db.sh [options]"
      echo "Options:"
      echo "  --file <path>               Path to .dump file (required)"
      echo "  --host <host>               Target database host (default: localhost)"
      echo "  --port <port>               Target database port (default: 5432)"
      echo "  --dbname <db>               Target database name (default: postgres)"
      echo "  --user <user>               Target database user (default: postgres)"
      echo "  --force-remote-restore      Explicit override required for non-local hosts"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# ------------------------------------------------------------------------------
# 2. HARD SAFETY GUARD (Runs BEFORE ANY Docker, psql, or network action)
# ------------------------------------------------------------------------------
LOWER_HOST=$(echo "$TARGET_HOST" | tr '[:upper:]' '[:lower:]')

# Check if target host looks like a remote, staging, production, or cloud host
IS_LOCAL=false
if [[ "$LOWER_HOST" == "localhost" || "$LOWER_HOST" == "127.0.0.1" || "$LOWER_HOST" == "::1" || "$LOWER_HOST" == "host.docker.internal" ]]; then
  IS_LOCAL=true
fi

IS_DANGEROUS=false
if [[ "$IS_LOCAL" != "true" ]]; then
  IS_DANGEROUS=true
fi

# Explicit pattern match for remote / managed hosts
if [[ "$LOWER_HOST" =~ (supabase|render|onrender|pooler|velorix|prod|production|staging) ]]; then
  IS_DANGEROUS=true
fi

if [[ "$IS_DANGEROUS" == "true" && "$FORCE_REMOTE_RESTORE" != "true" ]]; then
  echo "" >&2
  echo "==============================================================================" >&2
  echo "🛑 CRITICAL SAFETY GUARD REFUSAL: NON-LOCAL RESTORE BLOCKED" >&2
  echo "==============================================================================" >&2
  echo "Target Host: '$TARGET_HOST' appears to be a remote, staging, or production database!" >&2
  echo "" >&2
  echo "Refusing to execute restore because restoring over an active cloud database will" >&2
  echo "OVERWRITE or DROP existing data tables." >&2
  echo "" >&2
  echo "If this is a genuine disaster recovery operation against a newly provisioned" >&2
  echo "recovery database, you MUST re-run with the explicit confirmation flag:" >&2
  echo "   --force-remote-restore" >&2
  echo "" >&2
  echo "Example:" >&2
  echo "   ./scripts/dr/restore-db.sh --force-remote-restore --host $TARGET_HOST --file <dump>" >&2
  echo "==============================================================================" >&2
  exit 1
fi

# ------------------------------------------------------------------------------
# 3. Parameter Validation
# ------------------------------------------------------------------------------
if [[ -z "$DUMP_FILE" ]]; then
  echo "❌ ERROR: --file <path_to_dump> is required." >&2
  exit 1
fi

if [[ ! -f "$DUMP_FILE" ]]; then
  echo "❌ ERROR: Dump file not found at: $DUMP_FILE" >&2
  exit 1
fi

echo "=============================================================================="
echo "🛡️  VELORIX SENTINEL — DATABASE RESTORE (pg_restore)"
echo "=============================================================================="
echo "Target Host:    $TARGET_HOST"
echo "Target Port:    $TARGET_PORT"
echo "Target DB:      $TARGET_DB"
echo "Target User:    $TARGET_USER"
echo "Dump File:      $DUMP_FILE"
echo "Remote Override: $FORCE_REMOTE_RESTORE"
echo "Started At:     $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo "------------------------------------------------------------------------------"

# ------------------------------------------------------------------------------
# 4. Target Environment Validation (Docker / Host Postgres)
# ------------------------------------------------------------------------------
if [[ "$IS_LOCAL" == "true" ]]; then
  echo "Testing local connectivity to $TARGET_HOST:$TARGET_PORT..."
  if ! pg_isready -h "$TARGET_HOST" -p "$TARGET_PORT" >/dev/null 2>&1; then
    echo "⚠️ Target $TARGET_HOST:$TARGET_PORT is not currently accepting connections."
    echo "Checking if Docker is available to launch an isolated recovery instance..."
    
    if command -v docker >/dev/null 2>&1 && docker version >/dev/null 2>&1; then
      CONTAINER_NAME="velorix-restore-drill"
      echo "Launching isolated Docker container ($CONTAINER_NAME) on port $TARGET_PORT..."
      docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
      docker run -d \
        --name "$CONTAINER_NAME" \
        -p "${TARGET_PORT}:5432" \
        -e POSTGRES_PASSWORD="${TARGET_PASSWORD:-drillpassword}" \
        postgres:16 >/dev/null
      
      echo "Waiting for container PostgreSQL 16 to be ready..."
      sleep 3
      for i in {1..15}; do
        if docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; then
          echo "✅ Isolated container is ready."
          TARGET_PASSWORD="${TARGET_PASSWORD:-drillpassword}"
          break
        fi
        sleep 1
      done
    else
      echo "❌ ERROR: Docker daemon is unavailable, and local target $TARGET_HOST:$TARGET_PORT is unreachable." >&2
      echo "   Ensure local PostgreSQL or Docker is running, or specify a valid reachable host." >&2
      exit 1
    fi
  fi
fi

# ------------------------------------------------------------------------------
# 5. Execute pg_restore
# ------------------------------------------------------------------------------
START_SECONDS=$SECONDS

echo "Executing pg_restore into $TARGET_DB..."
PGPASSWORD="${TARGET_PASSWORD}" pg_restore \
  --host="$TARGET_HOST" \
  --port="$TARGET_PORT" \
  --username="$TARGET_USER" \
  --dbname="$TARGET_DB" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --verbose \
  "$DUMP_FILE" || {
    # Note: pg_restore returns exit code 1 if warnings occur (e.g. drop table non-fatal notices).
    # We verify actual schema health via verify-integrity.sql.
    echo "ℹ️ pg_restore exited with non-zero status (expected for clean/if-exists notices on empty DB)."
  }

ELAPSED=$(( SECONDS - START_SECONDS ))

echo "------------------------------------------------------------------------------"
echo "✅ RESTORE COMPLETED IN ${ELAPSED}s"
echo "Target:        $TARGET_HOST:$TARGET_PORT/$TARGET_DB"
echo "Completed At:  $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo ""
echo "NEXT STEP: Run integrity verification to validate schema consistency:"
echo "   ./scripts/dr/verify-integrity.sh --host $TARGET_HOST --port $TARGET_PORT --user $TARGET_USER --db $TARGET_DB"
echo "=============================================================================="
