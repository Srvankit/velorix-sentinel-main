#!/usr/bin/env bash
# ==============================================================================
# Velorix Sentinel — Storage Reconciliation Runner (Bash)
# Task 8.3: Disaster Recovery Storage Reconciler Runner
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v bun >/dev/null 2>&1; then
  bun run "${SCRIPT_DIR}/reconcile-storage.ts" "$@"
elif command -v node >/dev/null 2>&1; then
  node "${SCRIPT_DIR}/reconcile-storage.ts" "$@"
else
  echo "❌ ERROR: Neither bun nor node was found to execute reconcile-storage.ts" >&2
  exit 1
fi
