@echo off
:: ====================================================================
:: ASTRA EDR - Allow Phone Remote & Target Laptop LAN Access
:: Opens Inbound Ports 5173 (Frontend) & 8080 (Backend) in Windows Firewall
:: ====================================================================
echo.
echo ====================================================================
echo   ASTRA EDR - ENABLING WIRELESS PHONE ACCESS IN WINDOWS FIREWALL
echo ====================================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] Please right-click this file and select "Run as administrator".
    echo.
    pause
    exit /b 1
)

echo [*] Opening Inbound Port 5173 (React Frontend Dashboard)...
netsh advfirewall firewall delete rule name="Astra-Vite-Frontend-5173" >nul 2>&1
netsh advfirewall firewall add rule name="Astra-Vite-Frontend-5173" dir=in action=allow protocol=TCP localport=5173 profile=any >nul
if %errorlevel% equ 0 (
    echo     [OK] Port 5173 Inbound Allowed.
) else (
    echo     [WARN] Could not configure Port 5173.
)

echo [*] Opening Inbound Port 8080 (Spring Boot Backend API)...
netsh advfirewall firewall delete rule name="Astra-Spring-Backend-8080" >nul 2>&1
netsh advfirewall firewall add rule name="Astra-Spring-Backend-8080" dir=in action=allow protocol=TCP localport=8080 profile=any >nul
if %errorlevel% equ 0 (
    echo     [OK] Port 8080 Inbound Allowed.
) else (
    echo     [WARN] Could not configure Port 8080.
)

echo.
echo ====================================================================
echo   SUCCESS! WINDOWS FIREWALL IS CONFIGURED FOR INBOUND ACCESS!
echo ====================================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike '*Loopback*' -and $_.InterfaceAlias -notlike '*vEthernet*' -and $_.IPAddress -notlike '169.254*' -and $_.IPAddress -notlike '192.168.56*' } | Select-Object -First 1).IPAddress; Write-Host ('Your Phone / Target Laptop can access ASTRA at: http://' + $ip + ':5173') -ForegroundColor Green; Write-Host ('Backend API is available at: http://' + $ip + ':8080') -ForegroundColor Green"
echo.
pause
