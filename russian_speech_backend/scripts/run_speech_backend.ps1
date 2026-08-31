$ErrorActionPreference = "Stop"

$backendRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path $backendRoot ".venv\Scripts\python.exe"
$logDirectory = Join-Path $backendRoot "logs"
$stdoutLog = Join-Path $logDirectory "speech-backend.stdout.log"
$stderrLog = Join-Path $logDirectory "speech-backend.stderr.log"

if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    throw "Backend Python executable was not found: $python"
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
Set-Location -LiteralPath $backendRoot

$ErrorActionPreference = "Continue"
& $python -m uvicorn app.main:app --host 0.0.0.0 --port 18000 1>> $stdoutLog 2>> $stderrLog
exit $LASTEXITCODE
