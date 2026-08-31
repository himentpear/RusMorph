$ErrorActionPreference = "Stop"

$proxyRoot = Split-Path -Parent $PSScriptRoot
$npx = "C:\Program Files\nodejs\npx.cmd"
$logDirectory = Join-Path $proxyRoot "logs"
$stdoutLog = Join-Path $logDirectory "agent-proxy.stdout.log"
$stderrLog = Join-Path $logDirectory "agent-proxy.stderr.log"

if (-not (Test-Path -LiteralPath $npx -PathType Leaf)) {
    throw "npx executable was not found: $npx"
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
Set-Location -LiteralPath $proxyRoot

$ErrorActionPreference = "Continue"
& $npx wrangler dev --ip 0.0.0.0 --port 8787 1>> $stdoutLog 2>> $stderrLog
exit $LASTEXITCODE
