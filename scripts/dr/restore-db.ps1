<#
.SYNOPSIS
    Velorix Sentinel -- Database Restore Script (PowerShell 5.1 / Windows).
    Task 8.3: Disaster Recovery Database Restore Utility.

.DESCRIPTION
    Restores a PostgreSQL custom-format dump into an isolated target database.
    Contains a strict safety guard: refuses to restore to remote/cloud hosts
    unless -ForceRemoteRestore is explicitly supplied.

.EXAMPLE
    # Refusal demonstration on dangerous remote host:
    .\scripts\dr\restore-db.ps1 -HostName "aws-0-ap-southeast-1.pooler.supabase.com" -DumpFile "backups\latest.dump"

.EXAMPLE
    # Explicit remote restore:
    .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName "aws-0-ap-southeast-1.pooler.supabase.com" -DumpFile "backups\latest.dump"
#>

[CmdletBinding()]
param(
    [string]$HostName = $(if ($env:DR_TARGET_HOST) { $env:DR_TARGET_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:DR_TARGET_PORT) { [int]$env:DR_TARGET_PORT } else { 5432 }),
    [string]$Database = $(if ($env:DR_TARGET_DB) { $env:DR_TARGET_DB } else { "postgres" }),
    [string]$UserName = $(if ($env:DR_TARGET_USER) { $env:DR_TARGET_USER } else { "postgres" }),
    [string]$Password = $env:DR_TARGET_PASSWORD,
    [string]$DumpFile,
    [switch]$ForceRemoteRestore
)

$ErrorActionPreference = "Stop"

# ------------------------------------------------------------------------------
# 1. HARD SAFETY GUARD (STRICT ALLOWLIST - Runs BEFORE ANY Docker/psql action)
# ------------------------------------------------------------------------------
$lowerHost = $HostName.ToLowerInvariant().Trim()

# STRICT ALLOWLIST: Only exact local targets permitted without explicit force flag
$allowedHosts = @("localhost", "127.0.0.1", "::1", "host.docker.internal")
$isAllowed = $allowedHosts -contains $lowerHost
$isLocal = $isAllowed

if (-not $isAllowed -and -not $ForceRemoteRestore) {
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Red
    Write-Host "CRITICAL SAFETY GUARD: NON-LOCAL RESTORE BLOCKED" -ForegroundColor Red
    Write-Host "==============================================================================" -ForegroundColor Red
    Write-Host "Target Host: '$HostName' is NOT in the local allowlist!" -ForegroundColor Yellow
    Write-Host "Permitted local targets without override: localhost, 127.0.0.1, ::1, host.docker.internal"
    Write-Host ""
    Write-Host "Refusing to execute restore. Restoring over any remote, shared, or unverified"
    Write-Host "database host risks OVERWRITING or DROPPING existing database tables."
    Write-Host ""
    Write-Host "If this is a genuine disaster recovery operation against a newly provisioned"
    Write-Host "recovery database, you MUST pass the explicit confirmation switch:"
    Write-Host "   -ForceRemoteRestore" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Example:"
    Write-Host "   .\scripts\dr\restore-db.ps1 -ForceRemoteRestore -HostName $HostName -DumpFile <dump>"
    Write-Host "==============================================================================" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 2. Parameter Validation
# ------------------------------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($DumpFile)) {
    Write-Error "DumpFile parameter (-DumpFile <path>) is required."
    exit 1
}

if (-not (Test-Path $DumpFile)) {
    Write-Error "Dump file not found at: $DumpFile"
    exit 1
}

# Resolve pg_restore executable
$pgRestoreCmd = "pg_restore"
if (-not (Get-Command "pg_restore" -ErrorAction SilentlyContinue)) {
    $standardPgPath = "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe"
    if (Test-Path $standardPgPath) {
        $pgRestoreCmd = $standardPgPath
    } else {
        $found = Get-ChildItem "C:\Program Files\PostgreSQL" -Recurse -Filter "pg_restore.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $pgRestoreCmd = $found.FullName
        } else {
            Write-Error "pg_restore executable was not found on PATH or in C:\Program Files\PostgreSQL."
            exit 1
        }
    }
}

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "VELORIX SENTINEL -- DATABASE RESTORE (pg_restore)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Target Host:     $HostName"
Write-Host "Target Port:     $Port"
Write-Host "Target DB:       $Database"
Write-Host "Target User:     $UserName"
Write-Host "Dump File:       $DumpFile"
Write-Host "Remote Override: $ForceRemoteRestore"
Write-Host "Started At:      $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ssZ'))"
Write-Host "------------------------------------------------------------------------------"

# ------------------------------------------------------------------------------
# 3. Local Target Availability Check
# ------------------------------------------------------------------------------
if ($isLocal) {
    Write-Host "Checking local connectivity to $($HostName):$Port..."
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    $connected = $false
    try {
        $asyncResult = $tcpClient.BeginConnect($HostName, $Port, $null, $null)
        $success = $asyncResult.AsyncWaitHandle.WaitOne(2000, $false)
        if ($success) {
            $tcpClient.EndConnect($asyncResult)
            $connected = $true
        }
    } catch {
        # Not connected
    } finally {
        $tcpClient.Close()
    }

    if (-not $connected) {
        Write-Warning "Target $($HostName):$Port is not currently accepting connections."
        Write-Host "Checking if Docker is available to launch an isolated recovery instance..."

        $dockerAvailable = $false
        try {
            $dockerVersion = & docker version 2>&1
            if ($LASTEXITCODE -eq 0) { $dockerAvailable = $true }
        } catch {}

        if ($dockerAvailable) {
            $containerName = "velorix-restore-drill"
            Write-Host "Launching isolated Docker container ($containerName) on port $Port..."
            & docker rm -f $containerName 2>&1 | Out-Null
            & docker run -d --name $containerName -p "${Port}:5432" -e POSTGRES_PASSWORD="drillpassword" postgres:16 | Out-Null
            Start-Sleep -Seconds 3
            Write-Host "[OK] Isolated container launched."
            if (-not $Password) { $Password = "drillpassword" }
        } else {
            Write-Error "Docker daemon is unavailable, and local target $($HostName):$Port is unreachable.`nEnsure local PostgreSQL or Docker is running, or specify a reachable host."
            exit 1
        }
    }
}

# ------------------------------------------------------------------------------
# 4. Execute pg_restore
# ------------------------------------------------------------------------------
$startTime = [System.Diagnostics.Stopwatch]::StartNew()

try {
    if ($Password) { $env:PGPASSWORD = $Password }

    Write-Host "Executing pg_restore into $Database..."
    & $pgRestoreCmd `
        --host="$HostName" `
        --port="$Port" `
        --username="$UserName" `
        --dbname="$Database" `
        --clean `
        --if-exists `
        --no-owner `
        --no-privileges `
        --verbose `
        "$DumpFile" 2>&1 | Out-Null

    $startTime.Stop()
    $elapsedSec = [math]::Round($startTime.Elapsed.TotalSeconds, 2)

    Write-Host "------------------------------------------------------------------------------"
    Write-Host "[OK] RESTORE COMPLETED IN ${elapsedSec}s" -ForegroundColor Green
    Write-Host "Target:        $($HostName):$Port/$Database"
    Write-Host "Completed At:  $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ssZ'))"
    Write-Host ""
    Write-Host "NEXT STEP: Run integrity verification to validate schema consistency:"
    Write-Host "   .\scripts\dr\verify-integrity.ps1 -HostName $HostName -Port $Port -UserName $UserName -Database $Database"
    Write-Host "=============================================================================="
} finally {
    $env:PGPASSWORD = $null
}
