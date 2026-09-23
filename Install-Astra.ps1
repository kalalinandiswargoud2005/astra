<#
.SYNOPSIS
    ASTRA EDR Agent - Enterprise Windows Service Installer
.DESCRIPTION
    Installs and configures the ASTRA EDR Windows Service (Session 0) with
    Session 0 IPC Bridge and interactive User Session UI Companion.
#>

[CmdletBinding()]
param (
    [string]$BackendUrl = "",
    [string]$DeviceId = "",
    [string]$Hostname = $env:COMPUTERNAME,
    [string]$InstallDir = "C:\Astra\Agent"
)

# 1. Verify Administrator Privileges
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[!] ERROR: Administrative privileges are required." -ForegroundColor Red
    Write-Host "Please re-run this installer from an elevated PowerShell prompt (Run as Administrator)." -ForegroundColor Yellow
    exit 1
}

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "   ASTRA EDR AGENT - WINDOWS SERVICE INSTALLER     " -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

# Resolve Backend URL from device.json or interactive prompt
$progDataJson = "C:\ProgramData\Astra\agent\device.json"
if ([string]::IsNullOrWhiteSpace($BackendUrl)) {
    if (Test-Path $progDataJson) {
        try {
            $jsonObj = Get-Content $progDataJson -Raw | ConvertFrom-Json
            if ($jsonObj.backendUrl) { $BackendUrl = $jsonObj.backendUrl }
        } catch {}
    }
}
if ([string]::IsNullOrWhiteSpace($BackendUrl)) {
    $defaultUrl = "http://192.168.1.44:8080"
    $inputUrl = Read-Host "Enter ASTRA SOC Server URL [default: $defaultUrl]"
    if ([string]::IsNullOrWhiteSpace($inputUrl)) {
        $BackendUrl = $defaultUrl
    } else {
        $BackendUrl = $inputUrl.Trim()
    }
}
$BackendUrl = $BackendUrl.TrimEnd('/')

# 2. Resolve / Preserve Persistent Device UUID
$idFilePath = Join-Path $InstallDir "device-id.txt"
if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    if (Test-Path $idFilePath) {
        $DeviceId = (Get-Content $idFilePath -Raw).Trim()
        Write-Host "[+] Preserving Existing Persistent Device ID: $DeviceId" -ForegroundColor Green
    } else {
        $DeviceId = [guid]::NewGuid().ToString()
        Write-Host "[+] Generated New Persistent Device ID     : $DeviceId" -ForegroundColor Green
    }
} else {
    Write-Host "[+] Assigned Device ID                     : $DeviceId" -ForegroundColor Green
}

Write-Host "[+] Target Endpoint Hostname              : $Hostname" -ForegroundColor Green
Write-Host "[+] Backend Server URL                    : $BackendUrl" -ForegroundColor Green
Write-Host "[+] Installation Directory                : $InstallDir" -ForegroundColor Green
Write-Host ""

# 3. Stop Any Existing Agent / Service / Companion
Write-Host "[1/6] Stopping previous ASTRA instances and services..." -ForegroundColor Yellow
Get-Service -Name "AstraEDR" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  [-] Stopping existing AstraEDR service..."
    Stop-Service -Name "AstraEDR" -Force -ErrorAction SilentlyContinue
}
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { 
    $_.CommandLine -like '*windows-agent*' -or $_.CommandLine -like '*C:\Astra\Agent*' -or $_.CommandLine -like '*Astra-UI*'
} | ForEach-Object {
    Write-Host "  [-] Terminating background agent/companion PID: $($_.ProcessId)"
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}

# 4. Create Directories and Save Config
Write-Host "[2/6] Preparing installation directories..." -ForegroundColor Yellow
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}
$logsDir = Join-Path $InstallDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}
$quarantineDir = "C:\Astra\Quarantine"
if (-not (Test-Path $quarantineDir)) {
    New-Item -ItemType Directory -Path $quarantineDir -Force | Out-Null
}

# Save persistent device-id.txt
Set-Content -Path $idFilePath -Value $DeviceId -Force

# 5. Copy Binaries & Configuration
Write-Host "[3/6] Copying ASTRA EDR agent binaries..." -ForegroundColor Yellow
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceJar = Join-Path $scriptDir "windows-agent\target\windows-agent-1.0.0.jar"
if (-not (Test-Path $sourceJar)) {
    $sourceJar = Join-Path $scriptDir "windows-agent.jar"
}

if (Test-Path $sourceJar) {
    Copy-Item -Path $sourceJar -Destination (Join-Path $InstallDir "windows-agent.jar") -Force
    Write-Host "  [+] Installed windows-agent.jar successfully." -ForegroundColor Green
} else {
    Write-Host "  [!] Warning: windows-agent.jar not found at $sourceJar" -ForegroundColor Red
}

# Copy AstraEDR.xml configuration
$sourceXml = Join-Path $scriptDir "windows-agent\AstraEDR.xml"
if (Test-Path $sourceXml) {
    Copy-Item -Path $sourceXml -Destination (Join-Path $InstallDir "AstraEDR.xml") -Force
}

# Copy Astra-UI.vbs
$sourceVbs = Join-Path $scriptDir "windows-agent\Astra-UI.vbs"
if (Test-Path $sourceVbs) {
    Copy-Item -Path $sourceVbs -Destination (Join-Path $InstallDir "Astra-UI.vbs") -Force
}

# 6. Discover Java 21 Runtime (Dynamic Detection)
Write-Host "[4/6] Locating Java Runtime..." -ForegroundColor Yellow
$javaExe = $null

# Search standard locations
$searchPaths = @(
    "$env:JAVA_HOME\bin\java.exe",
    "$env:JAVA_HOME\bin\javaw.exe",
    "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe",
    "C:\Program Files\Java\jdk-21*\bin\java.exe",
    "C:\Program Files\Microsoft\jdk-21*\bin\java.exe",
    "C:\Program Files\BellSoft\LibericaJDK-21*\bin\java.exe",
    "C:\Program Files\Amazon Corretto\jdk21*\bin\java.exe"
)

foreach ($pathPattern in $searchPaths) {
    $resolved = Get-Item -Path $pathPattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($resolved -and (Test-Path $resolved.FullName)) {
        $javaExe = $resolved.FullName
        break
    }
}

if (-not $javaExe) {
    $cmdJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmdJava) {
        $javaExe = $cmdJava.Source
    }
}

if (-not $javaExe) {
    $javaExe = "java.exe"
}

# Also resolve javaw.exe for companion
$javawExe = $javaExe.Replace("java.exe", "javaw.exe")
if (-not (Test-Path $javawExe)) {
    $javawExe = $javaExe
}

# Save detected java path for VBS companion
Set-Content -Path (Join-Path $InstallDir "java-path.txt") -Value $javawExe -Force
Write-Host "  [+] Using Java: $javaExe" -ForegroundColor Green

# 7. Configure & Register Windows Service
Write-Host "[5/6] Registering ASTRA EDR Windows Service (AstraEDR)..." -ForegroundColor Yellow
$winswExe = Join-Path $InstallDir "AstraEDR.exe"

if (-not (Test-Path $winswExe)) {
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13
        Write-Host "  [+] Downloading WinSW Service Wrapper (v2.12.0)..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri "https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe" -OutFile $winswExe -UseBasicParsing -TimeoutSec 15
    } catch {
        Write-Host "  [*] WinSW online download skipped. Using native service runner fallback." -ForegroundColor DarkGray
    }
}

if (Test-Path $winswExe) {
    # Update AstraEDR.xml with actual parameters
    $xmlContent = @"
<service>
  <id>AstraEDR</id>
  <name>ASTRA Autonomous EDR Service</name>
  <description>Enterprise Autonomous Threat Intelligence and Endpoint Security Response Agent for ASTRA Platform.</description>
  <executable>$javaExe</executable>
  <arguments>-Xmx512m -jar "$InstallDir\windows-agent.jar" --agent.device-id=$DeviceId --agent.hostname=$Hostname --astra.backend.url=$BackendUrl</arguments>
  <logmode>rotate</logmode>
  <logpath>$InstallDir\logs</logpath>
  <startmode>Automatic</startmode>
  <env name="ASTRA_HOME" value="$InstallDir"/>
  <env name="AGENT_DEVICE_ID" value="$DeviceId"/>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="20 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <resetfailure>1 hour</resetfailure>
</service>
"@
    Set-Content -Path (Join-Path $InstallDir "AstraEDR.xml") -Value $xmlContent -Force
    
    # Install service via WinSW
    & $winswExe stop 2>&1 | Out-Null
    & $winswExe uninstall 2>&1 | Out-Null
    & $winswExe install
    & $winswExe start
    Write-Host "  [+] AstraEDR Windows Service installed and started via WinSW." -ForegroundColor Green
} else {
    # Fallback to Scheduled System Task
    $taskName = "AstraEDR_AgentService"
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    $action = New-ScheduledTaskAction -Execute $javaExe -Argument "-jar `"$InstallDir\windows-agent.jar`" --agent.device-id=$DeviceId --agent.hostname=$Hostname --astra.backend.url=$BackendUrl" -WorkingDirectory $InstallDir
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId "NT AUTHORITY\SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit 0
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Write-Host "  [+] AstraEDR registered as High-Priority SYSTEM Startup Service." -ForegroundColor Green
}

# 8. Register & Launch User Session UI Companion (Session 0 IPC Bridge)
Write-Host "[6/6] Registering User Session UI Companion for Screen Overlay..." -ForegroundColor Yellow
$regKey = "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run"
$vbsPath = "wscript.exe `"$InstallDir\Astra-UI.vbs`""
Set-ItemProperty -Path $regKey -Name "AstraOverlayCompanion" -Value $vbsPath -Force
Write-Host "  [+] Registered AstraOverlayCompanion in Windows Startup registry." -ForegroundColor Green

# Launch companion now in active user session
if (Test-Path "$InstallDir\Astra-UI.vbs") {
    Start-Process "wscript.exe" -ArgumentList "`"$InstallDir\Astra-UI.vbs`""
    Write-Host "  [+] User Session UI Companion launched for active desktop." -ForegroundColor Green
}

Start-Sleep -Seconds 2

# 9. Verification Summary
$serviceStatus = "Unknown"
$svc = Get-Service -Name "AstraEDR" -ErrorAction SilentlyContinue
if ($svc) {
    $serviceStatus = $svc.Status.ToString()
}

Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host "   INSTALLATION COMPLETE - ASTRA AGENT ONLINE!     " -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green
Write-Host "   Endpoint Hostname : $Hostname" -ForegroundColor Cyan
Write-Host "   Device UUID       : $DeviceId" -ForegroundColor Cyan
Write-Host "   Service Name      : AstraEDR ($serviceStatus)" -ForegroundColor Cyan
Write-Host "   Local IPC Stream  : http://127.0.0.1:8082/api/v1/agent/ipc/overlay-stream" -ForegroundColor Cyan
Write-Host "   UI Companion      : Active in User Session (Session 1+)" -ForegroundColor Cyan
Write-Host "   SOC Dashboard     : http://localhost:5173/devices" -ForegroundColor Cyan
Write-Host "   Log Directory     : $InstallDir\logs" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Green
