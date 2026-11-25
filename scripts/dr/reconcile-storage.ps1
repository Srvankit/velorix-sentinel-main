<#
.SYNOPSIS
    Velorix Sentinel — Storage Reconciliation Runner (PowerShell 5.1 / Windows).
    Task 8.3: Disaster Recovery Storage Reconciler Runner.
#>

[CmdletBinding()]
param(
    [switch]$Help
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$reconcileScript = Join-Path $scriptDir "reconcile-storage.ts"

if ($Help) {
    if (Get-Command bun -ErrorAction SilentlyContinue) {
        & bun run $reconcileScript --help
    } else {
        Write-Host "Runs storage reconciliation comparing database uploaded_files to S3 bucket objects."
    }
    exit 0
}

if (Get-Command bun -ErrorAction SilentlyContinue) {
    & bun run $reconcileScript
} elseif (Get-Command node -ErrorAction SilentlyContinue) {
    & node $reconcileScript
} else {
    Write-Error "Neither bun nor node was found on PATH to execute reconcile-storage.ts"
    exit 1
}
