<#
.SYNOPSIS
    Velorix Sentinel -- Integrity Verification Runner (PowerShell 5.1 / Windows).
    Task 8.3: Post-Restore Database Consistency Check Runner.
#>

[CmdletBinding()]
param(
    [string]$HostName = $(if ($env:DR_TARGET_HOST) { $env:DR_TARGET_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:DR_TARGET_PORT) { [int]$env:DR_TARGET_PORT } else { 5432 }),
    [string]$Database = $(if ($env:DR_TARGET_DB) { $env:DR_TARGET_DB } else { "postgres" }),
    [string]$UserName = $(if ($env:DR_TARGET_USER) { $env:DR_TARGET_USER } else { "postgres" }),
    [string]$Password = $env:DR_TARGET_PASSWORD
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlFile = Join-Path $scriptDir "verify-integrity.sql"

if (-not (Test-Path $sqlFile)) {
    Write-Error "SQL file not found at: $sqlFile"
    exit 1
}

# Resolve psql executable
$psqlCmd = "psql"
if (-not (Get-Command "psql" -ErrorAction SilentlyContinue)) {
    $standardPgPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
    if (Test-Path $standardPgPath) {
        $psqlCmd = $standardPgPath
    } else {
        $found = Get-ChildItem "C:\Program Files\PostgreSQL" -Recurse -Filter "psql.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $psqlCmd = $found.FullName
        } else {
            Write-Error "psql executable was not found on PATH or in C:\Program Files\PostgreSQL."
            exit 1
        }
    }
}

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "RUNNING POST-RESTORE INTEGRITY VERIFICATION" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Target Host: $($HostName):$Port"
Write-Host "Database:    $Database"
Write-Host "User:        $UserName"
Write-Host "SQL Script:  $sqlFile"
Write-Host "------------------------------------------------------------------------------"

try {
    if ($Password) { $env:PGPASSWORD = $Password }
    & $psqlCmd `
        --host="$HostName" `
        --port="$Port" `
        --username="$UserName" `
        --dbname="$Database" `
        --file="$sqlFile"
} finally {
    $env:PGPASSWORD = $null
}
