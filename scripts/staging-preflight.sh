#!/usr/bin/env bash
# ==============================================================================
# Velorix Sentinel — Staging Preflight Verification Test Suite
#
# Usage:
#   ./scripts/staging-preflight.sh <staging_frontend_url> <staging_backend_url> [supabase_url] [supabase_anon_key]
#
# Example:
#   ./scripts/staging-preflight.sh https://velorix-sentinel-staging.netlify.app https://velorix-sentinel-backend-staging.onrender.com
# ==============================================================================

set -euo pipefail

# ANSI color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

FRONTEND_URL="${1:-${STAGING_FRONTEND_URL:-}}"
BACKEND_URL="${2:-${STAGING_BACKEND_URL:-}}"
SUPABASE_URL="${3:-${STAGING_SUPABASE_URL:-}}"
SUPABASE_ANON_KEY="${4:-${STAGING_SUPABASE_ANON_KEY:-}}"

if [[ -z "$FRONTEND_URL" || -z "$BACKEND_URL" ]]; then
    echo -e "${RED}Error: Missing required arguments.${NC}"
    echo "Usage: $0 <staging_frontend_url> <staging_backend_url> [supabase_url] [supabase_anon_key]"
    echo "Or set STAGING_FRONTEND_URL and STAGING_BACKEND_URL in environment."
    exit 1
fi

# Strip trailing slashes
FRONTEND_URL="${FRONTEND_URL%/}"
BACKEND_URL="${BACKEND_URL%/}"
SUPABASE_URL="${SUPABASE_URL%/}"

PASSED_COUNT=0
FAILED_COUNT=0
SKIPPED_COUNT=0

log_header() {
    echo -e "\n${BLUE}==============================================================================${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${BLUE}==============================================================================${NC}"
}

log_pass() {
    echo -e "  [${GREEN}PASS${NC}] $1"
    PASSED_COUNT=$((PASSED_COUNT + 1))
}

log_fail() {
    echo -e "  [${RED}FAIL${NC}] $1: $2"
    FAILED_COUNT=$((FAILED_COUNT + 1))
}

log_skip() {
    echo -e "  [${YELLOW}SKIP${NC}] $1: $2"
    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
}

log_info() {
    echo -e "  [INFO] $1"
}

log_header "VELORIX SENTINEL — STAGING PREFLIGHT VERIFICATION"
echo "Target Frontend: $FRONTEND_URL"
echo "Target Backend:  $BACKEND_URL"
if [[ -n "$SUPABASE_URL" ]]; then
    echo "Target Supabase: $SUPABASE_URL"
fi
echo ""

# ------------------------------------------------------------------------------
# Check 1: Cold-Start Retry Loop (Render Free-Tier Wake-Up)
# ------------------------------------------------------------------------------
log_header "Check 1: Backend Cold-Start & Health Probe"
MAX_RETRIES=12
RETRY_DELAY=10
BACKEND_HEALTHY=false

log_info "Probing $BACKEND_URL/actuator/health with up to $MAX_RETRIES attempts (${RETRY_DELAY}s interval)..."

for i in $(seq 1 $MAX_RETRIES); do
    HTTP_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 15 "$BACKEND_URL/actuator/health" 2>/dev/null || echo "000")
    if [[ "$HTTP_CODE" == "200" ]]; then
        BACKEND_HEALTHY=true
        log_pass "Backend responded with HTTP 200 on attempt $i ($(((i - 1) * RETRY_DELAY))s elapsed)"
        break
    else
        log_info "Attempt $i/$MAX_RETRIES: HTTP status $HTTP_CODE. Waiting ${RETRY_DELAY}s for cold-start..."
        sleep "$RETRY_DELAY"
    fi
done

if [[ "$BACKEND_HEALTHY" != "true" ]]; then
    log_fail "Check 1" "Backend failed to respond with HTTP 200 within $((MAX_RETRIES * RETRY_DELAY))s. Cold-start timed out or backend is down."
    echo -e "\n${RED}Aborting remaining tests because backend is unreachable.${NC}"
    exit 1
fi

# ------------------------------------------------------------------------------
# Check 2: Health & Readiness Payload Verification
# ------------------------------------------------------------------------------
log_header "Check 2: Health & Readiness Status Verification"
HEALTH_BODY=$(curl -s -k --max-time 10 "$BACKEND_URL/actuator/health" 2>/dev/null || echo "")
if [[ "$HEALTH_BODY" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
    log_pass "Actuator health payload confirms status: UP"
else
    log_fail "Check 2" "Actuator health payload did not contain status: UP. Received: $HEALTH_BODY"
fi

# ------------------------------------------------------------------------------
# Check 3: Swagger / OpenAPI Gating (404/401/403 Expected in Prod/Staging)
# ------------------------------------------------------------------------------
log_header "Check 3: Swagger / OpenAPI Gating"
SWAGGER_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 10 "$BACKEND_URL/swagger-ui/index.html" 2>/dev/null || echo "000")
DOCS_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 10 "$BACKEND_URL/v3/api-docs" 2>/dev/null || echo "000")

if [[ "$SWAGGER_CODE" == "404" || "$SWAGGER_CODE" == "401" || "$SWAGGER_CODE" == "403" ]]; then
    log_pass "Swagger UI gated successfully (HTTP $SWAGGER_CODE)"
else
    log_fail "Check 3" "Swagger UI is publicly accessible in staging/prod profile! HTTP $SWAGGER_CODE"
fi

if [[ "$DOCS_CODE" == "404" || "$DOCS_CODE" == "401" || "$DOCS_CODE" == "403" ]]; then
    log_pass "OpenAPI API docs gated successfully (HTTP $DOCS_CODE)"
else
    log_fail "Check 3" "OpenAPI api-docs are publicly accessible in staging/prod profile! HTTP $DOCS_CODE"
fi

# ------------------------------------------------------------------------------
# Check 4: Strict CORS Probe
# ------------------------------------------------------------------------------
log_header "Check 4: Strict CORS Enforcement"

# Probe evil origin
EVIL_CORS_HEADER=$(curl -s -k -I -H "Origin: https://evil.example.com" --max-time 10 "$BACKEND_URL/actuator/health" 2>/dev/null | grep -i "access-control-allow-origin" || true)

if [[ -z "$EVIL_CORS_HEADER" ]]; then
    log_pass "Disallowed origin https://evil.example.com received no Access-Control-Allow-Origin header"
elif [[ "$EVIL_CORS_HEADER" =~ evil\.example\.com || "$EVIL_CORS_HEADER" =~ \* ]]; then
    log_fail "Check 4" "Insecure CORS! Origin https://evil.example.com was allowed: $EVIL_CORS_HEADER"
else
    log_pass "Disallowed origin received non-matching CORS header"
fi

# Probe legitimate staging frontend origin
STAGING_CORS_HEADER=$(curl -s -k -I -H "Origin: $FRONTEND_URL" --max-time 10 "$BACKEND_URL/api/auth/me" 2>/dev/null | grep -i "access-control-allow-origin" || true)

if [[ "$STAGING_CORS_HEADER" =~ $FRONTEND_URL ]]; then
    log_pass "Allowed staging frontend origin $FRONTEND_URL received matching CORS header"
else
    log_fail "Check 4" "Staging origin $FRONTEND_URL did not receive matching Access-Control-Allow-Origin header. Got: $STAGING_CORS_HEADER"
fi

# ------------------------------------------------------------------------------
# Check 5: Security Headers on Frontend Site
# ------------------------------------------------------------------------------
log_header "Check 5: Frontend Security Headers & CSP"
FRONTEND_HEADERS=$(curl -s -k -I --max-time 10 "$FRONTEND_URL" 2>/dev/null || echo "")

# Content-Security-Policy
if echo "$FRONTEND_HEADERS" | grep -iq "content-security-policy:"; then
    CSP_VAL=$(echo "$FRONTEND_HEADERS" | grep -i "content-security-policy:" | tr -d '\r')
    if echo "$CSP_VAL" | grep -q "$BACKEND_URL"; then
        log_pass "Content-Security-Policy contains connect-src with staging backend origin"
    else
        log_fail "Check 5" "CSP does not include staging backend URL ($BACKEND_URL). CSP: $CSP_VAL"
    fi
else
    log_fail "Check 5" "Frontend response missing Content-Security-Policy header"
fi

# X-Content-Type-Options
if echo "$FRONTEND_HEADERS" | grep -iq "x-content-type-options:[[:space:]]*nosniff"; then
    log_pass "X-Content-Type-Options: nosniff present"
else
    log_fail "Check 5" "Frontend missing X-Content-Type-Options: nosniff header"
fi

# X-Frame-Options or frame-ancestors
if echo "$FRONTEND_HEADERS" | grep -iq "x-frame-options:[[:space:]]*deny" || echo "$FRONTEND_HEADERS" | grep -iq "frame-ancestors[[:space:]]*'none'"; then
    log_pass "Clickjacking protection present (X-Frame-Options or CSP frame-ancestors)"
else
    log_fail "Check 5" "Frontend missing clickjacking protection headers"
fi

# Strict-Transport-Security
if echo "$FRONTEND_HEADERS" | grep -iq "strict-transport-security:"; then
    log_pass "Strict-Transport-Security (HSTS) present"
else
    log_fail "Check 5" "Frontend missing Strict-Transport-Security header"
fi

# ------------------------------------------------------------------------------
# Check 6: Frontend Config Baked Verification (No localhost references)
# ------------------------------------------------------------------------------
log_header "Check 6: Frontend Delivered Assets (No localhost leak)"
INDEX_HTML=$(curl -s -k --max-time 10 "$FRONTEND_URL" 2>/dev/null || echo "")

if [[ "$INDEX_HTML" =~ localhost:8080 || "$INDEX_HTML" =~ 127\.0\.0\.1:8080 ]]; then
    log_fail "Check 6" "Frontend index.html references localhost:8080 directly!"
else
    log_pass "Frontend index.html contains no localhost:8080 references"
fi

# ------------------------------------------------------------------------------
# Check 7: Unauthenticated Endpoint Gating (401 Unauthorized expected)
# ------------------------------------------------------------------------------
log_header "Check 7: Unauthenticated API Endpoint Gating"
CAPTURES_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 10 "$BACKEND_URL/api/captures" 2>/dev/null || echo "000")
DASHBOARD_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 10 "$BACKEND_URL/api/dashboard/stats" 2>/dev/null || echo "000")

if [[ "$CAPTURES_CODE" == "401" ]]; then
    log_pass "GET /api/captures correctly returns HTTP 401 Unauthorized"
else
    log_fail "Check 7" "GET /api/captures returned unexpected HTTP status: $CAPTURES_CODE (expected 401)"
fi

if [[ "$DASHBOARD_CODE" == "401" ]]; then
    log_pass "GET /api/dashboard/stats correctly returns HTTP 401 Unauthorized"
else
    log_fail "Check 7" "GET /api/dashboard/stats returned unexpected HTTP status: $DASHBOARD_CODE (expected 401)"
fi

# ------------------------------------------------------------------------------
# Check 8: Rate Limiter Burst Test
# ------------------------------------------------------------------------------
log_header "Check 8: Login Rate Limiter Burst Test"
log_info "Firing 6 rapid POST requests to /api/auth/login to verify IP rate limit enforcement..."

BURST_429_DETECTED=false
LAST_STATUS="000"

for attempt in {1..6}; do
    LAST_STATUS=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 5 \
        -X POST "$BACKEND_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"email":"preflight-rate-limit-check@velorix.net","password":"InvalidPreflightPassword123!"}' \
        2>/dev/null || echo "000")
    
    log_info "Burst attempt $attempt/6 returned HTTP $LAST_STATUS"
    if [[ "$LAST_STATUS" == "429" ]]; then
        BURST_429_DETECTED=true
        break
    fi
done

if [[ "$BURST_429_DETECTED" == "true" ]]; then
    log_pass "Rate limiter successfully engaged and returned HTTP 429 Too Many Requests"
else
    # In some reverse proxy environments, rate limiter may key on X-Forwarded-For
    log_fail "Check 8" "Rate limiter failed to engage on 6 consecutive login attempts. Final status was $LAST_STATUS (expected 429)"
fi

# ------------------------------------------------------------------------------
# Check 9: Supabase Direct RLS Probe (Optional)
# ------------------------------------------------------------------------------
log_header "Check 9: Supabase Row-Level Security (RLS) Direct Probe"
if [[ -n "$SUPABASE_URL" && -n "$SUPABASE_ANON_KEY" ]]; then
    log_info "Testing Supabase REST API directly with public anon key..."
    SB_RESP=$(curl -s -k --max-time 10 \
        -H "apikey: $SUPABASE_ANON_KEY" \
        -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
        "$SUPABASE_URL/rest/v1/captures" 2>/dev/null || echo "ERROR")
    
    # RLS should either return empty array [] or 401/403 or error object
    if [[ "$SB_RESP" == "[]" || "$SB_RESP" =~ "row-level security" || "$SB_RESP" =~ "PGRST" || "$SB_RESP" =~ "permission denied" ]]; then
        log_pass "Supabase RLS prevents anonymous capture table extraction (Returned: $SB_RESP)"
    else
        log_fail "Check 9" "Supabase RLS check failed! Anon key returned data: $SB_RESP"
    fi
else
    log_skip "Check 9" "Supabase URL and anon key not provided; skipping direct PostgREST RLS probe"
fi

# ------------------------------------------------------------------------------
# Check 10: HTTP Method Tampering (405 Method Not Allowed)
# ------------------------------------------------------------------------------
log_header "Check 10: HTTP Method Tampering & Gating"
METHOD_CODE=$(curl -s -k -o /dev/null -w "%{http_code}" --max-time 10 \
    -X PUT "$BACKEND_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"test":true}' 2>/dev/null || echo "000")

if [[ "$METHOD_CODE" == "405" ]]; then
    log_pass "Unsupported PUT /api/auth/login returned HTTP 405 Method Not Allowed"
elif [[ "$METHOD_CODE" == "404" || "$METHOD_CODE" == "403" || "$METHOD_CODE" == "401" ]]; then
    log_pass "Unsupported PUT request safely rejected with HTTP $METHOD_CODE"
else
    log_fail "Check 10" "Unsupported HTTP method produced unexpected response: HTTP $METHOD_CODE (expected 405)"
fi

# ------------------------------------------------------------------------------
# Summary & Exit
# ------------------------------------------------------------------------------
log_header "PREFLIGHT VERIFICATION SUMMARY"
echo -e "  Passed checks:  ${GREEN}$PASSED_COUNT${NC}"
echo -e "  Failed checks:  ${RED}$FAILED_COUNT${NC}"
echo -e "  Skipped checks: ${YELLOW}$SKIPPED_COUNT${NC}"

if [[ "$FAILED_COUNT" -gt 0 ]]; then
    echo -e "\n${RED}PREFLIGHT VERIFICATION FAILED with $FAILED_COUNT failure(s). Review logs above.${NC}"
    exit 1
else
    echo -e "\n${GREEN}ALL MANDATORY PREFLIGHT CHECKS PASSED SUCCESSFULLY.${NC}"
    exit 0
fi
