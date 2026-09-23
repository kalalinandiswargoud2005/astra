@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title ASTRA EDR - Connection Diagnostic Tool

echo ====================================================================
echo              ASTRA EDR - SOC CONNECTION DIAGNOSTIC
echo ====================================================================
echo.

set "SAVED_BACKEND="
if exist "C:\ProgramData\Astra\agent\device.json" (
    for /f "tokens=2 delims=:, " %%a in ('findstr /i "backendUrl" "C:\ProgramData\Astra\agent\device.json"') do (
        set "SAVED_BACKEND=%%~a"
    )
)

set "DEFAULT_IP=192.168.1.44"
if defined SAVED_BACKEND (
    for /f "tokens=2 delims=/: " %%i in ("!SAVED_BACKEND!") do set "DEFAULT_IP=%%i"
)

set /p "BACKEND_IP=Enter SOC Laptop IP address [default: %DEFAULT_IP%]: "
if "%BACKEND_IP%"=="" set "BACKEND_IP=%DEFAULT_IP%"

echo.
echo [1/3] Testing Wi-Fi Ping to SOC Host Laptop (%BACKEND_IP%)...
ping -n 2 %BACKEND_IP% >nul 2>&1
if %errorLevel% equ 0 (
    echo [OK] Target laptop can ping %BACKEND_IP% successfully!
) else (
    echo [FAIL] Target laptop CANNOT ping %BACKEND_IP%.
    echo Please make sure both laptops are on the SAME Wi-Fi or Mobile Hotspot network.
)
echo.

echo [2/3] Testing ASTRA Backend API (http://%BACKEND_IP%:8080/api/v1/devices)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = [System.Net.WebRequest]::Create('http://%BACKEND_IP%:8080/api/v1/devices'); $r.Timeout = 4000; $resp = $r.GetResponse(); Write-Host '[OK] Successfully reached ASTRA Backend API on port 8080!' -ForegroundColor Green; $resp.Close(); } catch { Write-Host ('[FAIL] Could not reach backend API on port 8080: ' + $_.Exception.Message) -ForegroundColor Red; Write-Host '  Hint: Run ALLOW_PHONE_FIREWALL.bat on the SOC laptop to open port 8080 in Windows Firewall.' -ForegroundColor Yellow; }"

echo.
echo [3/3] Checking if Agent is currently running locally on this laptop...
tasklist /FI "IMAGENAME eq javaw.exe" 2>NUL | find /I /N "javaw.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo [OK] ASTRA Agent background process (javaw.exe) is RUNNING.
) else (
    echo [WARN] ASTRA Agent is not running. Run INSTALL_ASTRA.bat to start it.
)

echo.
echo ====================================================================
pause
