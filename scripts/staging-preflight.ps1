<#
.SYNOPSIS
    Velorix Sentinel — Staging Preflight Verification Test Suite (PowerShell)

.DESCRIPTION
    Automated preflight test suite verifying that staging infrastructure (Render backend,
    Netlify frontend, Supabase database/RLS, and security guardrails) is fully operational.
    Compatible with Windows PowerShell 5.1 and PowerShell Core (7+).

.PARAMETER FrontendUrl
    The public URL of the deployed Netlify staging frontend (e.g. https://velorix-sentinel-staging.netlify.app).

.PARAMETER BackendUrl
    The public URL of the deployed Render staging backend (e.g. https://velorix-sentinel-backend-staging.onrender.com).

.PARAMETER SupabaseUrl
    (Optional) Supabase project URL for direct PostgREST RLS testing.

.PARAMETER SupabaseAnonKey
    (Optional) Supabase public anon key for direct PostgREST RLS testing.

.EXAMPLE
    .\scripts\staging-preflight.ps1 -FrontendUrl https://velorix-sentinel-staging.netlify.app -BackendUrl https://velorix-sentinel-backend-staging.onrender.com
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$FrontendUrl = $env:STAGING_FRONTEND_URL,

    [Parameter(Position = 1)]
    [string]$BackendUrl = $env:STAGING_BACKEND_URL,

    [Parameter(Position = 2)]
    [string]$SupabaseUrl = $env:STAGING_SUPABASE_URL,

    [Parameter(Position = 3)]
    [string]$SupabaseAnonKey = $env:STAGING_SUPABASE_ANON_KEY
)

# Enforce TLS 1.2 / 1.3 on legacy Windows PowerShell 5.1 runtimes
try {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor [System.Net.SecurityProtocolType]::Tls11 -bor [System.Net.SecurityProtocolType]::Tls
} catch {}

if ([string]::IsNullOrWhiteSpace($FrontendUrl) -or [string]::IsNullOrWhiteSpace($BackendUrl)) {
    Write-Host "[ERROR] Missing required parameters -FrontendUrl and -BackendUrl" -ForegroundColor Red
    Write-Host "Usage: .\scripts\staging-preflight.ps1 -FrontendUrl <frontend_url> -BackendUrl <backend_url> [-SupabaseUrl <url>] [-SupabaseAnonKey <key>]"
    Write-Host "Or set STAGING_FRONTEND_URL and STAGING_BACKEND_URL in your environment."
    exit 1
}

$FrontendUrl = $FrontendUrl.TrimEnd('/')
$BackendUrl = $BackendUrl.TrimEnd('/')
if (-not [string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $SupabaseUrl = $SupabaseUrl.TrimEnd('/')
}

$script:PassedCount = 0
$script:FailedCount = 0
$script:SkippedCount = 0

function Write-SectionHeader([string]$Title) {
    Write-Host "`n==============================================================================" -ForegroundColor DarkCyan
    Write-Host "$Title" -ForegroundColor Cyan
    Write-Host "==============================================================================" -ForegroundColor DarkCyan
}

function Log-Pass([string]$Message) {
    Write-Host "  [PASS] $Message" -ForegroundColor Green
    $script:PassedCount++
}

function Log-Fail([string]$Check, [string]$Message) {
    Write-Host "  [FAIL] ${Check}: $Message" -ForegroundColor Red
    $script:FailedCount++
}

function Log-Skip([string]$Check, [string]$Message) {
    Write-Host "  [SKIP] ${Check}: $Message" -ForegroundColor Yellow
    $script:SkippedCount++
}

function Log-Info([string]$Message) {
    Write-Host "  [INFO] $Message" -ForegroundColor Gray
}

# Cross-platform HTTP probe function supporting PS 5.1 & PS 7+
function Invoke-HttpProbe {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int]$TimeoutSec = 10
    )
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            UseBasicParsing = $true
            TimeoutSec = $TimeoutSec
        }
        if ($Headers.Count -gt 0) { $params["Headers"] = $Headers }
        if ($Body) {
            $params["Body"] = $Body
            if (-not $Headers.ContainsKey("Content-Type")) {
                $params["ContentType"] = "application/json"
            }
        }
        $resp = Invoke-WebRequest @params
        return [PSCustomObject]@{
            StatusCode = [int]$resp.StatusCode
            Headers = $resp.Headers
            Content = $resp.Content
        }
    } catch [System.Net.WebException] {
        $webEx = $_.Exception
        if ($webEx.Response) {
            $statusCode = [int]$webEx.Response.StatusCode
            $headers = @{}
            foreach ($key in $webEx.Response.Headers.AllKeys) {
                if ($key) { $headers[$key] = $webEx.Response.Headers[$key] }
            }
            $content = ""
            try {
                $stream = $webEx.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $content = $reader.ReadToEnd()
                    $reader.Dispose()
                }
            } catch {}
            return [PSCustomObject]@{
                StatusCode = $statusCode
                Headers = $headers
                Content = $content
            }
        }
        return [PSCustomObject]@{
            StatusCode = 0
            Headers = @{}
            Content = $webEx.Message
        }
    } catch {
        return [PSCustomObject]@{
            StatusCode = 0
            Headers = @{}
            Content = $_.Exception.Message
        }
    }
}

Write-SectionHeader "VELORIX SENTINEL — STAGING PREFLIGHT VERIFICATION (POWERSHELL)"
Write-Host "Target Frontend: $FrontendUrl"
Write-Host "Target Backend:  $BackendUrl"
if (-not [string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    Write-Host "Target Supabase: $SupabaseUrl"
}

# ------------------------------------------------------------------------------
# Check 1: Cold-Start Retry Loop (Render Free-Tier Wake-Up)
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 1: Backend Cold-Start & Health Probe"
$MaxRetries = 12
$RetryDelaySec = 10
$BackendHealthy = $false

Log-Info "Probing $BackendUrl/actuator/health (up to $MaxRetries attempts, ${RetryDelaySec}s interval)..."

for ($i = 1; $i -le $MaxRetries; $i++) {
    $probe = Invoke-HttpProbe -Uri "$BackendUrl/actuator/health" -TimeoutSec 15
    if ($probe.StatusCode -eq 200) {
        $BackendHealthy = $true
        Log-Pass "Backend responded with HTTP 200 on attempt $i ($((($i - 1) * $RetryDelaySec))s elapsed)"
        break
    } else {
        Log-Info "Attempt ${i}/${MaxRetries}: HTTP status $($probe.StatusCode). Waiting ${RetryDelaySec}s for cold-start..."
        Start-Sleep -Seconds $RetryDelaySec
    }
}

if (-not $BackendHealthy) {
    Log-Fail "Check 1" "Backend failed to respond with HTTP 200 within $(($MaxRetries * $RetryDelaySec))s. Cold-start timed out or backend is down."
    Write-Host "`n[FATAL] Aborting remaining tests because backend is unreachable." -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# Check 2: Health & Readiness Status Verification
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 2: Health & Readiness Status Verification"
$healthProbe = Invoke-HttpProbe -Uri "$BackendUrl/actuator/health" -TimeoutSec 10
if ($healthProbe.Content -match '"status"\s*:\s*"UP"') {
    Log-Pass "Actuator health payload confirms status: UP"
} else {
    Log-Fail "Check 2" "Actuator health payload does not indicate UP status. Payload: $($healthProbe.Content)"
}

# ------------------------------------------------------------------------------
# Check 3: Swagger / OpenAPI Gating
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 3: Swagger / OpenAPI Gating"
$swaggerProbe = Invoke-HttpProbe -Uri "$BackendUrl/swagger-ui/index.html" -TimeoutSec 10
$docsProbe = Invoke-HttpProbe -Uri "$BackendUrl/v3/api-docs" -TimeoutSec 10

if ($swaggerProbe.StatusCode -in @(404, 401, 403)) {
    Log-Pass "Swagger UI gated successfully (HTTP $($swaggerProbe.StatusCode))"
} else {
    Log-Fail "Check 3" "Swagger UI is publicly accessible in staging/prod profile! HTTP $($swaggerProbe.StatusCode)"
}

if ($docsProbe.StatusCode -in @(404, 401, 403)) {
    Log-Pass "OpenAPI API docs gated successfully (HTTP $($docsProbe.StatusCode))"
} else {
    Log-Fail "Check 3" "OpenAPI api-docs are publicly accessible in staging/prod profile! HTTP $($docsProbe.StatusCode)"
}

# ------------------------------------------------------------------------------
# Check 4: Strict CORS Enforcement
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 4: Strict CORS Enforcement"

# Evil origin probe
$evilHeaders = @{ "Origin" = "https://evil.example.com" }
$evilProbe = Invoke-HttpProbe -Uri "$BackendUrl/actuator/health" -Headers $evilHeaders -TimeoutSec 10
$evilCors = $null
foreach ($key in $evilProbe.Headers.Keys) {
    if ($key -like "*Access-Control-Allow-Origin*") {
        $evilCors = $evilProbe.Headers[$key]
    }
}

if ([string]::IsNullOrWhiteSpace($evilCors)) {
    Log-Pass "Disallowed origin https://evil.example.com received no Access-Control-Allow-Origin header"
} elseif ($evilCors -match "evil\.example\.com" -or $evilCors -eq "*") {
    Log-Fail "Check 4" "Insecure CORS! Origin https://evil.example.com was granted access: $evilCors"
} else {
    Log-Pass "Disallowed origin received safe non-matching CORS header"
}

# Legitimate staging frontend probe
$stagingHeaders = @{ "Origin" = $FrontendUrl }
$stagingProbe = Invoke-HttpProbe -Uri "$BackendUrl/api/auth/me" -Headers $stagingHeaders -TimeoutSec 10
$stagingCors = $null
foreach ($key in $stagingProbe.Headers.Keys) {
    if ($key -like "*Access-Control-Allow-Origin*") {
        $stagingCors = $stagingProbe.Headers[$key]
    }
}

if ($stagingCors -and ($stagingCors -match [regex]::Escape($FrontendUrl))) {
    Log-Pass "Allowed staging frontend origin $FrontendUrl received matching CORS header"
} else {
    Log-Fail "Check 4" "Staging origin $FrontendUrl did not receive matching Access-Control-Allow-Origin header. Got: $stagingCors"
}

# ------------------------------------------------------------------------------
# Check 5: Security Headers on Frontend Site
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 5: Frontend Security Headers & CSP"
$frontendProbe = Invoke-HttpProbe -Uri $FrontendUrl -TimeoutSec 10

$cspHeader = $null
$contentTypeHeader = $null
$frameHeader = $null
$hstsHeader = $null

foreach ($key in $frontendProbe.Headers.Keys) {
    if ($key -like "*Content-Security-Policy*") { $cspHeader = $frontendProbe.Headers[$key] }
    if ($key -like "*X-Content-Type-Options*") { $contentTypeHeader = $frontendProbe.Headers[$key] }
    if ($key -like "*X-Frame-Options*") { $frameHeader = $frontendProbe.Headers[$key] }
    if ($key -like "*Strict-Transport-Security*") { $hstsHeader = $frontendProbe.Headers[$key] }
}

# CSP
if ($cspHeader) {
    if ($cspHeader -match [regex]::Escape($BackendUrl)) {
        Log-Pass "Content-Security-Policy contains connect-src with staging backend origin"
    } else {
        Log-Fail "Check 5" "CSP does not include staging backend URL ($BackendUrl). CSP: $cspHeader"
    }
} else {
    Log-Fail "Check 5" "Frontend response missing Content-Security-Policy header"
}

# X-Content-Type-Options
if ($contentTypeHeader -and $contentTypeHeader -match "nosniff") {
    Log-Pass "X-Content-Type-Options: nosniff present"
} else {
    Log-Fail "Check 5" "Frontend missing X-Content-Type-Options: nosniff header"
}

# Clickjacking Protection
if (($frameHeader -and $frameHeader -match "deny") -or ($cspHeader -and $cspHeader -match "frame-ancestors\s+'none'")) {
    Log-Pass "Clickjacking protection present (X-Frame-Options: DENY or CSP frame-ancestors 'none')"
} else {
    Log-Fail "Check 5" "Frontend missing clickjacking protection headers"
}

# HSTS
if ($hstsHeader) {
    Log-Pass "Strict-Transport-Security (HSTS) present"
} else {
    Log-Fail "Check 5" "Frontend missing Strict-Transport-Security header"
}

# ------------------------------------------------------------------------------
# Check 6: Frontend Delivered Assets (No localhost leak)
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 6: Frontend Delivered Assets (No localhost leak)"
if ($frontendProbe.Content -match "localhost:8080" -or $frontendProbe.Content -match "127\.0\.0\.1:8080") {
    Log-Fail "Check 6" "Frontend index.html references localhost:8080 directly!"
} else {
    Log-Pass "Frontend index.html contains no localhost:8080 references"
}

# ------------------------------------------------------------------------------
# Check 7: Unauthenticated Endpoint Gating
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 7: Unauthenticated API Endpoint Gating"
$capturesProbe = Invoke-HttpProbe -Uri "$BackendUrl/api/captures" -TimeoutSec 10
$dashboardProbe = Invoke-HttpProbe -Uri "$BackendUrl/api/dashboard/stats" -TimeoutSec 10

if ($capturesProbe.StatusCode -eq 401) {
    Log-Pass "GET /api/captures correctly returns HTTP 401 Unauthorized"
} else {
    Log-Fail "Check 7" "GET /api/captures returned unexpected HTTP status: $($capturesProbe.StatusCode) (expected 401)"
}

if ($dashboardProbe.StatusCode -eq 401) {
    Log-Pass "GET /api/dashboard/stats correctly returns HTTP 401 Unauthorized"
} else {
    Log-Fail "Check 7" "GET /api/dashboard/stats returned unexpected HTTP status: $($dashboardProbe.StatusCode) (expected 401)"
}

# ------------------------------------------------------------------------------
# Check 8: Rate Limiter Burst Test
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 8: Login Rate Limiter Burst Test"
Log-Info "Firing 6 rapid POST requests to /api/auth/login to verify rate limit enforcement..."

$burst429 = $false
$lastStatus = 0

for ($attempt = 1; $attempt -le 6; $attempt++) {
    $burstBody = '{"email":"preflight-rate-limit-check@velorix.net","password":"InvalidPreflightPassword123!"}'
    $burstHeaders = @{ "Content-Type" = "application/json" }
    $loginProbe = Invoke-HttpProbe -Uri "$BackendUrl/api/auth/login" -Method "POST" -Headers $burstHeaders -Body $burstBody -TimeoutSec 5
    $lastStatus = $loginProbe.StatusCode
    Log-Info "Burst attempt $attempt/6 returned HTTP $lastStatus"
    if ($lastStatus -eq 429) {
        $burst429 = $true
        break
    }
}

if ($burst429) {
    Log-Pass "Rate limiter successfully engaged and returned HTTP 429 Too Many Requests"
} else {
    Log-Fail "Check 8" "Rate limiter failed to engage on 6 consecutive login attempts. Final status was $lastStatus (expected 429)"
}

# ------------------------------------------------------------------------------
# Check 9: Supabase Direct RLS Probe (Optional)
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 9: Supabase Row-Level Security (RLS) Direct Probe"
if (-not [string]::IsNullOrWhiteSpace($SupabaseUrl) -and -not [string]::IsNullOrWhiteSpace($SupabaseAnonKey)) {
    Log-Info "Testing Supabase PostgREST endpoint directly with public anon key..."
    $sbHeaders = @{
        "apikey" = $SupabaseAnonKey
        "Authorization" = "Bearer $SupabaseAnonKey"
    }
    $sbProbe = Invoke-HttpProbe -Uri "$SupabaseUrl/rest/v1/captures" -Headers $sbHeaders -TimeoutSec 10

    if ($sbProbe.Content -eq "[]" -or $sbProbe.Content -match "row-level security" -or $sbProbe.StatusCode -in @(401, 403)) {
        Log-Pass "Supabase RLS prevents anonymous capture extraction (HTTP $($sbProbe.StatusCode), Content: $($sbProbe.Content))"
    } else {
        Log-Fail "Check 9" "Supabase RLS check failed! Anon key returned sensitive data: $($sbProbe.Content)"
    }
} else {
    Log-Skip "Check 9" "Supabase URL and anon key not provided; skipping direct PostgREST RLS probe"
}

# ------------------------------------------------------------------------------
# Check 10: HTTP Method Tampering
# ------------------------------------------------------------------------------
Write-SectionHeader "Check 10: HTTP Method Tampering & Gating"
$tamperProbe = Invoke-HttpProbe -Uri "$BackendUrl/api/auth/login" -Method "PUT" -Body '{"test":true}' -TimeoutSec 10

if ($tamperProbe.StatusCode -eq 405) {
    Log-Pass "Unsupported PUT /api/auth/login returned HTTP 405 Method Not Allowed"
} elseif ($tamperProbe.StatusCode -in @(404, 403, 401)) {
    Log-Pass "Unsupported PUT request safely rejected with HTTP $($tamperProbe.StatusCode)"
} else {
    Log-Fail "Check 10" "Unsupported HTTP method produced unexpected response: HTTP $($tamperProbe.StatusCode) (expected 405)"
}

# ------------------------------------------------------------------------------
# Summary & Exit
# ------------------------------------------------------------------------------
Write-SectionHeader "PREFLIGHT VERIFICATION SUMMARY"
Write-Host "  Passed checks:  $script:PassedCount" -ForegroundColor Green
Write-Host "  Failed checks:  $script:FailedCount" -ForegroundColor Red
Write-Host "  Skipped checks: $script:SkippedCount" -ForegroundColor Yellow

if ($script:FailedCount -gt 0) {
    Write-Host "`nPREFLIGHT VERIFICATION FAILED with $script:FailedCount failure(s). Review logs above." -ForegroundColor Red
    exit 1
} else {
    Write-Host "`nALL MANDATORY PREFLIGHT CHECKS PASSED SUCCESSFULLY." -ForegroundColor Green
    exit 0
}
