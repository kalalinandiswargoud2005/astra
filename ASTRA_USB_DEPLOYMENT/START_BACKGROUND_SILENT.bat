@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title ASTRA EDR - Silent Background Runner

set "SAVED_BACKEND="
if exist "C:\ProgramData\Astra\agent\device.json" (
    for /f "tokens=2 delims=:, " %%a in ('findstr /i "backendUrl" "C:\ProgramData\Astra\agent\device.json"') do (
        set "SAVED_BACKEND=%%~a"
    )
)

if defined SAVED_BACKEND (
    set "BACKEND_URL=!SAVED_BACKEND!"
) else (
    set "BACKEND_URL=http://192.168.1.44:8080"
)

echo ====================================================================
echo                 ASTRA EDR - SILENT BACKGROUND LAUNCHER
echo ====================================================================
echo.
echo Target Connecting to SOC Server: %BACKEND_URL%
echo Launching agent via javaw (runs 24/7 silently without CMD window)...
echo.

taskkill /F /IM javaw.exe >nul 2>&1
timeout /t 1 >nul

start "" javaw.exe -Djava.awt.headless=false -Xmx512m -jar "%~dp0windows-agent.jar" --astra.backend.url=%BACKEND_URL%

echo [SUCCESS] ASTRA Agent is now running in the background!
echo [INFO] You can safely close this window now.
echo [INFO] Check your SOC Web Dashboard (/devices) - the laptop will be ONLINE.
echo.
timeout /t 3
exit
