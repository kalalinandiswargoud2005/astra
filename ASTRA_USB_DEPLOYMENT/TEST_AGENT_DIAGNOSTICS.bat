@echo off
setlocal enabledelayedexpansion
title ASTRA EDR - Target Laptop Health & Connectivity Diagnostics
color 0B
cls

echo ====================================================================
echo      ASTRA EDR - TARGET LAPTOP HEALTH & CONNECTIVITY CHECK
echo ====================================================================
echo.

:: 1. Check Administrator Rights
net session >nul 2>&1
if %errorLevel% equ 0 (
    echo  [+] Privilege Level     : Administrator (ELEVATED)
) else (
    echo  [*] Privilege Level     : Standard User (Run as Administrator for full tests)
)

:: 2. Check Java Runtime
where java.exe >nul 2>&1
if %errorLevel% equ 0 (
    for /f "tokens=*" %%i in ('java -version 2^>^&1') do (
        echo  [+] Java Version        : %%i
        goto :after_java
    )
) else (
    echo  [!] Java Status         : NOT FOUND in system PATH!
)
:after_java

:: 3. Read Configured Backend URL
set "BACKEND_URL="
if exist "C:\ProgramData\Astra\agent\device.json" (
    for /f "tokens=2 delims=:, " %%a in ('findstr /i "backendUrl" "C:\ProgramData\Astra\agent\device.json"') do (
        set "BACKEND_URL=%%~a"
    )
)
if not defined BACKEND_URL set "BACKEND_URL=http://localhost:8080"
echo  [+] Configured SOC URL  : %BACKEND_URL%

:: 4. Test Network Reachability to SOC Server
echo.
echo [*] Testing HTTP connection to SOC Server (%BACKEND_URL%)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$u = '%BACKEND_URL%/api/v1/devices'; try { $r = [System.Net.WebRequest]::Create($u); $r.Timeout = 3000; $resp = $r.GetResponse(); Write-Host '  [+] SOC Connection OK  : Response HTTP 200 (Reachable)' -ForegroundColor Green; $resp.Close(); } catch { Write-Host '  [!] SOC Connection FAIL: Cannot reach ' $u -ForegroundColor Red; Write-Host '      Check: 1) Is SOC laptop running? 2) Is firewall port 8080 open?' -ForegroundColor Yellow; }"

:: 5. Check Agent Process Status
echo.
echo [*] Checking local ASTRA Agent process...
tasklist /FI "IMAGENAME eq javaw.exe" 2>nul | findstr /i "javaw.exe" >nul
if %errorLevel% equ 0 (
    echo  [+] Agent Process       : ACTIVE (javaw.exe is running in background)
) else (
    echo  [!] Agent Process       : NOT RUNNING! Run INSTALL_ASTRA.bat to start.
)

:: 6. Check Local IPC Port 8082
netstat -ano | findstr ":8082 " >nul 2>&1
if %errorLevel% equ 0 (
    echo  [+] Local IPC Port 8082 : LISTENING (Ready for local commands & HUD overlay)
) else (
    echo  [*] Local IPC Port 8082 : Not listening yet (Agent may still be initializing)
)

echo.
echo ====================================================================
echo  Diagnostics Complete. Press any key to exit.
echo ====================================================================
pause >nul
