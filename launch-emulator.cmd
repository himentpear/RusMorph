@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\launch_android_emulator.ps1" %*
if errorlevel 1 (
    echo.
    echo Emulator launcher failed. See the error above.
    pause
    exit /b 1
)

echo.
echo WeRus is ready for visual acceptance.
timeout /t 3 >nul
