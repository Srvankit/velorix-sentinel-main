<#
.SYNOPSIS
    Velorix Sentinel -- Database Backup Script (PowerShell 5.1 / Windows).
    Task 8.3: Disaster Recovery Database Dump Utility.

.DESCRIPTION
    Executes a custom-format pg_dump against the target PostgreSQL / Supabase database.
    Saves output to the backups directory with size and timing reports.
    Passwords and secrets are never printed.

.EXAMPLE
    $env:DR_DB_HOST = "aws-0-ap-southeast-1.pooler.supabase.com"
    $env:DR_DB_PORT = "5432"
    $env:DR_DB_NAME = "postgres"
    $env:DR_DB_USER = "postgres.abcdefghijklmnopqrst"
    $env:DR_DB_PASSWORD = "StrongStagingPassword"
    .\scripts\dr\backup-db.ps1
#>

[CmdletBinding()]
param(
    [string]$HostName = $env:DR_DB_HOST,
    [int]$Port = $(if ($env:DR_DB_PORT) { [int]$env:DR_DB_PORT } else { 5432 }),
    [string]$Database = $(if ($env:DR_DB_NAME) { $env:DR_DB_NAME } else { "postgres" }),
    [string]$UserName = $env:DR_DB_USER,
    [string]$Password = $env:DR_DB_PASSWORD,
    [string]$OutputDir = $(if ($env:DR_BACKUP_DIR) { $env:DR_BACKUP_DIR } else { "backups" })
)

$ErrorActionPreference = "Stop"

# Fallback environment variable aliases
if (-not $HostName -and $env:SUPABASE_DB_HOST) { $HostName = $env:SUPABASE_DB_HOST }
if (-not $UserName -and $env:SUPABASE_DB_USERNAME) { $UserName = $env:SUPABASE_DB_USERNAME }
if (-not $Password -and $env:PGPASSWORD) { $Password = $env:PGPASSWORD }
if (-not $Password -and $env:SUPABASE_DB_PASSWORD) { $Password = $env:SUPABASE_DB_PASSWORD }

# Validation
if ([string]::IsNullOrWhiteSpace($HostName)) {
    Write-Error "DR_DB_HOST is required. Set -HostName, `$env:DR_DB_HOST, or `$env:SUPABASE_DB_HOST."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($UserName)) {
    Write-Error "DR_DB_USER is required. Set -UserName, `$env:DR_DB_USER, or `$env:SUPABASE_DB_USERNAME."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Password)) {
    Write-Error "Database password is required. Set -Password, `$env:DR_DB_PASSWORD, or `$env:PGPASSWORD."
    exit 1
}

# Resolve pg_dump executable
$pgDumpCmd = "pg_dump"
if (-not (Get-Command "pg_dump" -ErrorAction SilentlyContinue)) {
    $standardPgPath = "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe"
    if (Test-Path $standardPgPath) {
        $pgDumpCmd = $standardPgPath
    } else {
        $found = Get-ChildItem "C:\Program Files\PostgreSQL" -Recurse -Filter "pg_dump.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $pgDumpCmd = $found.FullName
        } else {
            Write-Error "pg_dump executable was not found on PATH or in C:\Program Files\PostgreSQL."
            exit 1
        }
    }
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outputFile = Join-Path $OutputDir "velorix_db_${timestamp}.dump"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "VELORIX SENTINEL -- DATABASE BACKUP (pg_dump)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Host:         $HostName"
Write-Host "Port:         $Port"
Write-Host "Database:     $Database"
Write-Host "User:         $UserName"
Write-Host "Output Path:  $outputFile"
Write-Host "Format:       PostgreSQL Custom Format (-Fc, public schema, --no-owner, --no-privileges)"
Write-Host "Started At:   $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ssZ'))"
Write-Host "------------------------------------------------------------------------------"

if ($HostName -match "pooler.supabase.com" -and $Port -ne 5432) {
    Write-Warning "Supabase pooler host detected with port $Port. Ensure session pooler (port 5432) is used."
}

$startTime = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $env:PGPASSWORD = $Password
    & $pgDumpCmd `
        --host="$HostName" `
        --port="$Port" `
        --username="$UserName" `
        --dbname="$Database" `
        --format=custom `
        --no-owner `
        --no-privileges `
        --schema=public `
        --file="$outputFile"

    $startTime.Stop()
    $elapsedSec = [math]::Round($startTime.Elapsed.TotalSeconds, 2)

    if (Test-Path $outputFile) {
        $fileItem = Get-Item $outputFile
        $sizeBytes = $fileItem.Length
        $sizeMB = [math]::Round($sizeBytes / (1024 * 1024), 2)

        Write-Host "------------------------------------------------------------------------------"
        Write-Host "[OK] BACKUP COMPLETED SUCCESSFULLY" -ForegroundColor Green
        Write-Host "Dump File:     $outputFile"
        Write-Host "Size:          $sizeBytes bytes (~$sizeMB MB)"
        Write-Host "Duration:      ${elapsedSec}s"
        Write-Host "Completed At:  $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ssZ'))"
        Write-Host "=============================================================================="
    } else {
        Write-Error "Backup file was not created at expected location."
        exit 1
    }
} finally {
    $env:PGPASSWORD = $null
}
