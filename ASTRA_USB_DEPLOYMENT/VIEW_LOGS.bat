@echo off
title ASTRA EDR - View Agent Logs
echo ====================================================================
echo                   ASTRA EDR AGENT REAL-TIME LOGS
echo ====================================================================
echo.

set "LOG_FILE="
if exist "C:\ProgramData\Astra\Agent\logs\agent.log" set "LOG_FILE=C:\ProgramData\Astra\Agent\logs\agent.log"
if not defined LOG_FILE (
    if exist "C:\Astra\Agent\logs\agent.log" set "LOG_FILE=C:\Astra\Agent\logs\agent.log"
)

if defined LOG_FILE (
    echo [OK] Found agent log file at !LOG_FILE!
    echo.
    echo --- LAST 40 LINES OF AGENT LOG ---
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Content '!LOG_FILE!' -Tail 40"
    echo.
) else (
    echo [NOTICE] Log file not found yet.
    echo Make sure the agent has been started with INSTALL_ASTRA.bat or START_BACKGROUND_SILENT.bat.
)

echo.
echo ====================================================================
pause
