param(
    [string]$Avd = "Pixel_9a",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

function Find-AndroidSdk {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" })
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    if (-not $candidates) {
        throw "Android SDK not found. Set ANDROID_SDK_ROOT or install the SDK from Android Studio."
    }
    return [string]($candidates | Select-Object -First 1)
}

function Get-RunningEmulator([string]$AdbPath) {
    $line = & $AdbPath devices | Select-String -Pattern '^(emulator-\d+)\s+device$' | Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) {
        return $line.Matches[0].Groups[1].Value
    }
    return $null
}

$sdk = Find-AndroidSdk
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"

if (-not (Test-Path -LiteralPath $adb)) { throw "adb not found: $adb" }
if (-not (Test-Path -LiteralPath $emulator)) { throw "Android Emulator not found: $emulator" }

$serial = Get-RunningEmulator $adb
if (-not $serial) {
    $availableAvds = @(& $emulator -list-avds)
    if (-not $availableAvds) { throw "No Android Virtual Device found. Create one in Android Studio Device Manager." }

    if ($Avd -notin $availableAvds) {
        Write-Host "AVD '$Avd' was not found. Using: $($availableAvds[0])" -ForegroundColor Yellow
        $Avd = $availableAvds[0]
    }

    Write-Host "Starting Android Emulator: $Avd" -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $Avd, "-no-boot-anim")

    $deadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 1
        $serial = Get-RunningEmulator $adb
        Write-Host "." -NoNewline
    } until ($serial -or (Get-Date) -gt $deadline)
    Write-Host
    if (-not $serial) { throw "The emulator did not connect to adb within two minutes." }
} else {
    Write-Host "Reusing the running emulator: $serial" -ForegroundColor Cyan
}

Write-Host "Waiting for Android to finish booting..." -ForegroundColor Cyan
$bootDeadline = (Get-Date).AddMinutes(2)
do {
    $bootCompleted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompleted -eq "1") { break }
    Start-Sleep -Seconds 1
} until ((Get-Date) -gt $bootDeadline)
if ($bootCompleted -ne "1") { throw "Android did not finish booting within two minutes." }

if (-not $SkipBuild) {
    Write-Host "Building the localDebug APK..." -ForegroundColor Cyan
    Push-Location $projectRoot
    try {
        & "$projectRoot\gradlew.bat" assembleLocalDebug
        if ($LASTEXITCODE -ne 0) { throw "The Gradle build failed." }
    } finally {
        Pop-Location
    }
}

$apk = Get-ChildItem -LiteralPath "$projectRoot\app\build\outputs\apk\local\debug" -Filter "*.apk" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) { throw "No localDebug APK was found. Retry without -SkipBuild." }

Write-Host "Installing: $($apk.Name)" -ForegroundColor Cyan
& $adb -s $serial install -r $apk.FullName
if ($LASTEXITCODE -ne 0) { throw "APK installation failed." }

Write-Host "Launching WeRus..." -ForegroundColor Green
& $adb -s $serial shell am force-stop org.namchieh.rusmorph
& $adb -s $serial shell am start -n org.namchieh.rusmorph/.MainActivity
if ($LASTEXITCODE -ne 0) { throw "The application failed to launch." }

Write-Host "Visual acceptance environment is ready: $serial / $Avd" -ForegroundColor Green
