$ErrorActionPreference = "SilentlyContinue"

$taskName = "RusMorph Speech Backend"
$backendRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $backendRoot "logs"
$watchdogLog = Join-Path $logDirectory "speech-backend-watchdog.log"

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$response = Invoke-WebRequest `
    -Uri "http://127.0.0.1:18000/health" `
    -UseBasicParsing `
    -TimeoutSec 10

if ($response.StatusCode -eq 200) {
    exit 0
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
Add-Content -LiteralPath $watchdogLog -Value "$timestamp health check failed; restarting backend"

Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
$listeners = Get-NetTCPConnection -LocalPort 18000 -State Listen -ErrorAction SilentlyContinue
foreach ($listener in $listeners) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($process.CommandLine -match "uvicorn\s+app\.main:app.*--port\s+18000") {
        Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2
Start-ScheduledTask -TaskName $taskName
