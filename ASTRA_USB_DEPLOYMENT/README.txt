================================================================================
                    ASTRA EDR — USB DEPLOYMENT PACKAGE
================================================================================

This folder contains everything needed to deploy and test ASTRA EDR on any Windows target laptop.

--------------------------------------------------------------------------------
HOW TO DEPLOY ON TARGET LAPTOP (3 STEPS):
--------------------------------------------------------------------------------

STEP 1: PREPARE USB
   - Copy this entire folder to a USB drive.

STEP 2: PLUG INTO TARGET LAPTOP
   - Connect target laptop and SOC laptop to the SAME Wi-Fi or Mobile Hotspot.
   - Run "ALLOW_PHONE_FIREWALL.bat" on the SOC laptop once to open firewall port 8080.

STEP 3: 1-CLICK INSTALLATION
   - Open the USB drive on the target laptop.
   - RIGHT-CLICK "INSTALL_ASTRA.bat" -> "Run as administrator".
   - Enter your SOC Server IP (e.g. http://192.168.1.44:8080) when prompted, or press ENTER.
   - Done! The target laptop is now permanently connected to the SOC Control Room.

--------------------------------------------------------------------------------
VERIFYING CONNECTION:
--------------------------------------------------------------------------------
1. Look at your SOC Dashboard (http://localhost:5173/devices) - the laptop will be ONLINE.
2. Run "TEST_AGENT_DIAGNOSTICS.bat" to run a 5-point health check anytime.
3. Run "VIEW_LOGS.bat" to inspect live agent activity.

--------------------------------------------------------------------------------
FILES IN THIS USB FOLDER:
--------------------------------------------------------------------------------
1. INSTALL_ASTRA.bat           -> 1-Click Automated Setup (Auto-starts on reboot)
2. UNINSTALL_ASTRA.bat         -> Complete Clean Removal (Removes services and files)
3. TEST_AGENT_DIAGNOSTICS.bat  -> 5-point health, network, and process check
4. TEST_CONNECTION.bat         -> Ping and HTTP verification to SOC laptop
5. START_FOREGROUND_DEBUG.bat  -> Interactive live log console for presentations
6. START_BACKGROUND_SILENT.bat -> Quick restart in silent background mode
7. VIEW_LOGS.bat               -> Live agent event logs
8. ALLOW_PHONE_FIREWALL.bat    -> Firewall opener for SOC laptop
9. windows-agent.jar           -> Standalone ASTRA EDR Engine (Java 21)
10. Astra-UI.vbs               -> Desktop HUD Companion Launcher
================================================================================
