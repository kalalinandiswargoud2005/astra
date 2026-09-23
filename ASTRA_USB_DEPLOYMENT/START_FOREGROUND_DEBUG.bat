@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title ASTRA EDR - Live Agent Console (Debug Mode)

echo ====================================================================
echo             ASTRA EDR AGENT - LIVE DEBUG CONSOLE
echo ====================================================================
echo.
echo Starting ASTRA EDR Agent in foreground...
echo You will see registration and heartbeat logs in real-time.
echo.

set "SAVED_BACKEND="
if exist "C:\ProgramData\Astra\agent\device.json" (
    for /f "tokens=2 delims=:, " %%a in ('findstr /i "backendUrl" "C:\ProgramData\Astra\agent\device.json"') do (
        set "SAVED_BACKEND=%%~a"
    )
)

set "DEFAULT_BACKEND=http://192.168.1.44:8080"
if defined SAVED_BACKEND (
    set "DEFAULT_BACKEND=!SAVED_BACKEND!"
)

set /p "USER_BACKEND=Backend URL [default: %DEFAULT_BACKEND%]: "

if "%USER_BACKEND%"=="" (
    set "BACKEND_URL=%DEFAULT_BACKEND%"
) else (
    set "BACKEND_URL=%USER_BACKEND%"
)

if "%BACKEND_URL:~-1%"=="/" set "BACKEND_URL=%BACKEND_URL:~0,-1%"

echo.
echo Connecting to: %BACKEND_URL%
echo.

java -Djava.awt.headless=false -jar "%~dp0windows-agent.jar" --astra.backend.url=%BACKEND_URL%
pause
