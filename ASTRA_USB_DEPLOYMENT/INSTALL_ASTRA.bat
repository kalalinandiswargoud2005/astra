@echo off
setlocal enabledelayedexpansion

:: 1. Check & Auto-elevate to Administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd.exe -ArgumentList '/c cd /d \"%~dp0\" && \"%~f0\"' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"
title ASTRA EDR - Enterprise 1-Click Target Laptop Deployment
color 0A

echo ====================================================================
echo                 ASTRA EDR - 1-CLICK USB DEPLOYMENT
echo                 Autonomous Endpoint Security Agent
echo ====================================================================
echo.

:: 2. Locate Java 21+ Runtime (Dynamic Search)
echo [1/5] Locating Java Runtime Environment...
set "JAVA_EXE="
where java.exe >nul 2>&1
if %errorLevel% equ 0 (
    for /f "tokens=*" %%i in ('where java.exe') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%i"
    )
)

if not defined JAVA_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

if not defined JAVA_EXE (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-21*" "C:\Program Files\Java\jdk-21*" "C:\Program Files\Microsoft\jdk-21*" "C:\Program Files\BellSoft\LibericaJDK-21*") do (
        if exist "%%d\bin\java.exe" (
            set "JAVA_EXE=%%d\bin\java.exe"
            goto :java_found
        )
    )
)

:java_found
if not defined JAVA_EXE (
    echo [!] ERROR: Java 21 or higher is not found on this target laptop.
    echo Please install OpenJDK 21 / Oracle JDK 21 on this machine first.
    echo.
    pause
    exit /b 1
)

set "JAVAW_EXE=!JAVA_EXE:java.exe=javaw.exe!"
if not exist "!JAVAW_EXE!" set "JAVAW_EXE=!JAVA_EXE!"

echo  [+] Java Runtime located: !JAVA_EXE!
echo.

:: 3. Configure SOC Server URL
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

echo --------------------------------------------------------------------
echo Enter the ASTRA SOC Server URL (e.g. http://192.168.1.44:8080)
echo If your SOC Laptop is at default, press ENTER:
echo --------------------------------------------------------------------
set /p "USER_BACKEND=SOC Server URL [default: %DEFAULT_BACKEND%]: "

if "%USER_BACKEND%"=="" (
    set "BACKEND_URL=%DEFAULT_BACKEND%"
) else (
    set "BACKEND_URL=%USER_BACKEND%"
)

:: Trim trailing slash
if "%BACKEND_URL:~-1%"=="/" set "BACKEND_URL=%BACKEND_URL:~0,-1%"

echo.
echo [CONFIG] Target will connect to: %BACKEND_URL%

:: Test Connectivity to SOC Backend
echo [2/5] Testing network route to SOC server (%BACKEND_URL%)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$res = 0; try { $req = [System.Net.WebRequest]::Create('%BACKEND_URL%/api/v1/devices'); $req.Timeout = 3000; $resp = $req.GetResponse(); $res = [int]$resp.StatusCode; $resp.Close(); } catch { $res = 0 }; if ($res -ge 200 -and $res -lt 400) { Write-Host '  [+] SOC Backend is REACHABLE and ONLINE!' -ForegroundColor Green } else { Write-Host '  [*] Notice: SOC Backend not immediately reachable. Agent will buffer alerts and auto-sync once connected.' -ForegroundColor Yellow }"

echo.

:: 4. Create Standard ASTRA Directories
echo [3/5] Initializing protected endpoint directories...
if not exist "C:\Astra\Agent" mkdir "C:\Astra\Agent"
if not exist "C:\Astra\Agent\logs" mkdir "C:\Astra\Agent\logs"
if not exist "C:\Astra\Demo" mkdir "C:\Astra\Demo"
if not exist "C:\ProgramData\Astra\agent" mkdir "C:\ProgramData\Astra\agent"
if not exist "C:\ProgramData\Astra\Agent\logs" mkdir "C:\ProgramData\Astra\Agent\logs"

:: 5. Copy Files to Target Laptop
echo [4/5] Deploying latest ASTRA binaries to C:\Astra\Agent\...
set "USB_DIR=%~dp0"
copy /Y "%USB_DIR%windows-agent.jar" "C:\Astra\Agent\windows-agent.jar" >nul
if exist "%USB_DIR%Astra-UI.vbs" copy /Y "%USB_DIR%Astra-UI.vbs" "C:\Astra\Agent\Astra-UI.vbs" >nul
echo !JAVAW_EXE!> "C:\Astra\Agent\java-path.txt"

:: Write persistent configuration file
(
  echo {
  echo   "backendUrl": "%BACKEND_URL%",
  echo   "hostname": "%COMPUTERNAME%",
  echo   "installedAt": "%date% %time%"
  echo }
) > "C:\ProgramData\Astra\agent\device.json"

:: 6. Clean Old/Conflicting Startup Mechanisms
echo [5/5] Configuring Single-Instance 24/7 Autostart...
:: Clean duplicate HKCU / HKLM Run entries to prevent dual-process launch
reg delete "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "AstraEDRAgent" /f >nul 2>&1
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "ASTRA_EDR_UI" /f >nul 2>&1
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "AstraOverlayCompanion" /f >nul 2>&1

:: Register clean Windows Scheduled Task (Runs on Logon with Highest Privileges, Single Instance)
schtasks /delete /tn "AstraEDRAgent" /f >nul 2>&1
schtasks /create /tn "AstraEDRAgent" /tr "\"!JAVAW_EXE!\" -Djava.awt.headless=false -Xmx512m -jar \"C:\Astra\Agent\windows-agent.jar\" --astra.backend.url=%BACKEND_URL%" /sc onlogon /rl highest /f >nul

:: Terminate any running previous agents
taskkill /F /IM javaw.exe >nul 2>&1
timeout /t 1 >nul

:: 7. Start Agent Silently in Background Now
echo [+] Launching ASTRA EDR agent in background...
start "" "!JAVAW_EXE!" -Djava.awt.headless=false -Xmx512m -jar "C:\Astra\Agent\windows-agent.jar" --astra.backend.url=%BACKEND_URL%

echo.
echo ====================================================================
echo       SUCCESS! ASTRA EDR IS NOW ONLINE ON THIS TARGET LAPTOP!
echo ====================================================================
echo.
echo  - Endpoint Hostname : %COMPUTERNAME%
echo  - SOC Server Target : %BACKEND_URL%
echo  - Service Status    : Active in background (Single Instance)
echo  - Autostart Mode    : High-Priority Windows Scheduled Task (On Logon)
echo.
echo Check the SOC Dashboard (/devices) on your main laptop:
echo "%COMPUTERNAME%" will now appear as ONLINE!
echo.
pause
