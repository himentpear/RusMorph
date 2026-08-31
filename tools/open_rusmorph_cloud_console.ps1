$ErrorActionPreference = "Continue"
$healthUri = "https://api.namchieh.org/health"
$apkPath = "D:\Folder\本研\APP\app\build\outputs\apk\debug\app-debug.apk"

try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new() } catch {}
$host.UI.RawUI.WindowTitle = "RusMorph Cloud Console"

function Show-CloudStatus {
    Clear-Host
    Write-Host "========================================" -ForegroundColor DarkCyan
    Write-Host "          RusMorph Cloud Console" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor DarkCyan
    try {
        $health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 10
        $ready = $health.status -eq "ok" -and $health.speech.configured
        Write-Host ("Cloud API    : " + $(if ($ready) { "READY" } else { "DEGRADED" })) -ForegroundColor $(if ($ready) { "Green" } else { "Yellow" })
        Write-Host ("Speech model : " + $health.speech.model)
        Write-Host ("Scoring mode : " + $health.speech.scoring)
        Write-Host "Local backend: DISABLED (Android uses Cloudflare)" -ForegroundColor DarkGray
    } catch {
        Write-Host "Cloud API    : UNREACHABLE" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Host "[R] Refresh   [A] Open APK folder   [Q] Close"
}

while ($true) {
    Show-CloudStatus
    $choice = [Console]::ReadKey($true).KeyChar.ToString().ToUpperInvariant()
    switch ($choice) {
        "R" { continue }
        "A" {
            if (Test-Path -LiteralPath $apkPath) {
                Start-Process explorer.exe -ArgumentList "/select,`"$apkPath`""
            }
        }
        "Q" { break }
    }
}
