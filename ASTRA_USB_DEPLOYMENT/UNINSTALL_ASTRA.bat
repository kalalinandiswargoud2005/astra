@echo off
cd /d "%~dp0"
title ASTRA EDR - 1-Click Uninstaller

:: Auto-elevate to Administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd.exe -ArgumentList '/c cd /d \"%~dp0\" && \"%~f0\"' -Verb RunAs"
    exit /b
)

echo ====================================================================
echo                 ASTRA EDR - CLEAN UNINSTALLER
echo ====================================================================
echo.

echo [1/3] Stopping background services and companion UI...
taskkill /F /IM javaw.exe >nul 2>&1

echo [2/3] Removing Windows startup and scheduled task entries...
reg delete "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "AstraEDRAgent" /f >nul 2>&1
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "ASTRA_EDR_UI" /f >nul 2>&1
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "AstraOverlayCompanion" /f >nul 2>&1
schtasks /delete /tn "AstraEDRAgent" /f >nul 2>&1
schtasks /delete /tn "AstraEDR_AgentService" /f >nul 2>&1

echo [3/3] Purging ASTRA binaries and demo sandboxes...
if exist "C:\Astra\Agent" rmdir /S /Q "C:\Astra\Agent" >nul 2>&1
if exist "C:\Astra\Demo" rmdir /S /Q "C:\Astra\Demo" >nul 2>&1

echo.
echo ====================================================================
echo      SUCCESS: ASTRA EDR HAS BEEN COMPLETELY REMOVED!
echo ====================================================================
echo.
pause
