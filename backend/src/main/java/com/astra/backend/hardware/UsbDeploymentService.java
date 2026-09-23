package com.astra.backend.hardware;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Slf4j
@Service
public class UsbDeploymentService {

    @Data
    public static class UsbDriveInfo {
        private String driveLetter;
        private String displayName;
        private long totalSpaceBytes;
        private long freeSpaceBytes;
        private boolean isRemovable;
    }

    /**
     * List all connected storage drives, highlighting removable USB drives
     */
    public List<UsbDriveInfo> getConnectedUsbDrives() {
        List<UsbDriveInfo> list = new ArrayList<>();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File[] roots = File.listRoots();

        if (roots != null) {
            for (File root : roots) {
                try {
                    UsbDriveInfo info = new UsbDriveInfo();
                    info.setDriveLetter(root.getAbsolutePath());
                    String name = fsv.getSystemDisplayName(root);
                    info.setDisplayName(name != null && !name.isEmpty() ? name : root.getAbsolutePath());
                    info.setTotalSpaceBytes(root.getTotalSpace());
                    info.setFreeSpaceBytes(root.getFreeSpace());
                    
                    boolean isDriveRemovable = !root.getAbsolutePath().toUpperCase().startsWith("C:");
                    info.setRemovable(isDriveRemovable);

                    list.add(info);
                } catch (Exception e) {
                    log.warn("Error inspecting root drive {}: {}", root, e.getMessage());
                }
            }
        }
        return list;
    }

    /**
     * Get Server Host IP (filters virtual/loopback adapters to prioritize true physical LAN IP)
     */
    public String getHostIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;

                String dName = ni.getDisplayName() != null ? ni.getDisplayName().toLowerCase() : "";
                String name = ni.getName() != null ? ni.getName().toLowerCase() : "";
                if (dName.contains("virtual") || dName.contains("vbox") || dName.contains("vmware") || 
                    dName.contains("hyper-v") || dName.contains("wsl") || name.startsWith("veth")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String hostAddr = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && hostAddr.indexOf(':') == -1 && 
                        !hostAddr.startsWith("169.254.") && !hostAddr.startsWith("192.168.56.")) {
                        return hostAddr;
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Deploy Astra Agent Installer payload to specified USB drive path
     */
    public boolean deployAgentToUsb(String drivePath, String targetHostname, String serverUrl, String customFolderName) {
        try {
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                serverUrl = "http://" + getHostIpAddress() + ":8080";
            }
            if (targetHostname == null || targetHostname.trim().isEmpty()) {
                targetHostname = "Target-Laptop-" + (int)(Math.random() * 9000 + 1000);
            }

            // Create custom or sanitized directory name
            String dirName;
            if (customFolderName != null && !customFolderName.trim().isEmpty()) {
                dirName = customFolderName.replaceAll("[^a-zA-Z0-9-_]", "_");
            } else {
                String safeHostname = targetHostname.replaceAll("[^a-zA-Z0-9-_]", "_");
                dirName = "ASTRA_AGENT_" + safeHostname;
            }
            
            File targetDir = new File(drivePath, dirName);
            
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    log.error("Failed to create directory on USB drive: {}", targetDir.getAbsolutePath());
                }
            }

            // Subdirectory for companion files
            File windowsAgentSubdir = new File(targetDir, "windows-agent");
            if (!windowsAgentSubdir.exists()) {
                windowsAgentSubdir.mkdirs();
            }

            File projectRoot = findProjectRoot();
            log.info("Resolved project root for USB staging: {}", projectRoot.getAbsolutePath());

            // 1. Copy windows-agent.jar
            File sourceJar = findFile(projectRoot, 
                "windows-agent.jar",
                "windows-agent/windows-agent.jar",
                "windows-agent/target/windows-agent-1.0.0.jar",
                "windows-agent/target/windows-agent.jar"
            );
            if (sourceJar != null && sourceJar.exists()) {
                Files.copy(sourceJar.toPath(), new File(targetDir, "windows-agent.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(sourceJar.toPath(), new File(targetDir, "windows-agent-1.0.0.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied windows-agent.jar to USB staging directory.");
            } else {
                log.warn("Source windows-agent.jar could not be located in project root: {}", projectRoot.getAbsolutePath());
            }

            // 2. Copy Core Scripts and Diagnostics
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/INSTALL_ASTRA.bat", "INSTALL_ASTRA.bat"), new File(targetDir, "INSTALL_ASTRA.bat"));
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/TEST_AGENT_DIAGNOSTICS.bat", "TEST_AGENT_DIAGNOSTICS.bat"), new File(targetDir, "TEST_AGENT_DIAGNOSTICS.bat"));
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/START_BACKGROUND_SILENT.bat", "START_BACKGROUND_SILENT.bat"), new File(targetDir, "START_BACKGROUND_SILENT.bat"));
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/START_FOREGROUND_DEBUG.bat", "START_FOREGROUND_DEBUG.bat"), new File(targetDir, "START_FOREGROUND_DEBUG.bat"));
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/TEST_CONNECTION.bat", "TEST_CONNECTION.bat"), new File(targetDir, "TEST_CONNECTION.bat"));
            copyIfExists(findFile(projectRoot, "ASTRA_USB_DEPLOYMENT/ALLOW_PHONE_FIREWALL.bat", "ALLOW_PHONE_FIREWALL.bat"), new File(targetDir, "ALLOW_PHONE_FIREWALL.bat"));
            copyIfExists(findFile(projectRoot, "Install-Astra.bat"), new File(targetDir, "Install-Astra.bat"));
            copyIfExists(findFile(projectRoot, "Install-Astra.ps1"), new File(targetDir, "Install-Astra.ps1"));
            copyIfExists(findFile(projectRoot, "Uninstall-Astra.bat"), new File(targetDir, "Uninstall-Astra.bat"));
            copyIfExists(findFile(projectRoot, "Uninstall-Astra.ps1"), new File(targetDir, "Uninstall-Astra.ps1"));
            copyIfExists(findFile(projectRoot, "start-agent.ps1"), new File(targetDir, "start-agent.ps1"));

            // 3. Copy windows-agent sub-files (AstraEDR.xml, Astra-UI.vbs)
            copyIfExists(findFile(projectRoot, "windows-agent/AstraEDR.xml", "AstraEDR.xml"), new File(windowsAgentSubdir, "AstraEDR.xml"));
            copyIfExists(findFile(projectRoot, "windows-agent/Astra-UI.vbs", "Astra-UI.vbs"), new File(windowsAgentSubdir, "Astra-UI.vbs"));
            // Also copy to root of targetDir for convenience
            copyIfExists(findFile(projectRoot, "windows-agent/AstraEDR.xml", "AstraEDR.xml"), new File(targetDir, "AstraEDR.xml"));
            copyIfExists(findFile(projectRoot, "windows-agent/Astra-UI.vbs", "Astra-UI.vbs"), new File(targetDir, "Astra-UI.vbs"));

            // 4. Generate Pre-configured One-Click Deploy-Target-Agent.bat
            File installScript = new File(targetDir, "Deploy-Target-Agent.bat");
            try (FileWriter writer = new FileWriter(installScript)) {
                writer.write("@echo off\r\n");
                writer.write("TITLE Astra EDR Agent — Automated USB Provisioning\r\n");
                writer.write("COLOR 0A\r\n");
                writer.write("cls\r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("echo     ASTRA EDR AGENT — AUTOMATED USB PROVISIONING   \r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("echo Target Device  : %COMPUTERNAME%\r\n");
                writer.write("echo C2 Backend URL : " + serverUrl + "\r\n");
                writer.write("echo.\r\n");
                writer.write("net session >nul 2>&1\r\n");
                writer.write("if %errorLevel% neq 0 (\r\n");
                writer.write("    echo [!] ERROR: Administrative privileges required!\r\n");
                writer.write("    echo Please right-click this script and select 'Run as Administrator'.\r\n");
                writer.write("    echo.\r\n");
                writer.write("    pause\r\n");
                writer.write("    exit /b 1\r\n");
                writer.write(")\r\n");
                writer.write("echo [+] Administrative rights verified.\r\n");
                writer.write("echo [+] Executing Automated ASTRA Windows Service Installer...\r\n");
                writer.write("echo.\r\n");
                writer.write("powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"%~dp0Install-Astra.ps1\" -BackendUrl \"" + serverUrl + "\" -Hostname \"%COMPUTERNAME%\"\r\n");
                writer.write("echo.\r\n");
                writer.write("pause\r\n");
            }

            // 5. Generate Pre-configured One-Click Uninstall-Target-Agent.bat
            File uninstallScript = new File(targetDir, "Uninstall-Target-Agent.bat");
            try (FileWriter writer = new FileWriter(uninstallScript)) {
                writer.write("@echo off\r\n");
                writer.write("TITLE Astra EDR Agent — Clean Uninstaller\r\n");
                writer.write("COLOR 0C\r\n");
                writer.write("cls\r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("echo     ASTRA EDR AGENT — CLEAN UNINSTALLATION        \r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("net session >nul 2>&1\r\n");
                writer.write("if %errorLevel% neq 0 (\r\n");
                writer.write("    echo [!] ERROR: Administrative privileges required!\r\n");
                writer.write("    echo Please right-click this script and select 'Run as Administrator'.\r\n");
                writer.write("    echo.\r\n");
                writer.write("    pause\r\n");
                writer.write("    exit /b 1\r\n");
                writer.write(")\r\n");
                writer.write("powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"%~dp0Uninstall-Astra.ps1\"\r\n");
                writer.write("echo.\r\n");
                writer.write("pause\r\n");
            }

            // 6. Generate Pre-configured One-Click Start-Target-Agent-Interactive.bat
            File interactiveScript = new File(targetDir, "Start-Target-Agent-Interactive.bat");
            try (FileWriter writer = new FileWriter(interactiveScript)) {
                writer.write("@echo off\r\n");
                writer.write("TITLE Astra EDR Agent — Quick Interactive Runner\r\n");
                writer.write("COLOR 0B\r\n");
                writer.write("cls\r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("echo     ASTRA EDR AGENT — INTERACTIVE RUNNER          \r\n");
                writer.write("echo ===================================================\r\n");
                writer.write("powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"%~dp0start-agent.ps1\" -BackendUrl \"" + serverUrl + "\"\r\n");
                writer.write("echo.\r\n");
                writer.write("pause\r\n");
            }

            // 7. Write README_INSTRUCTIONS.txt
            File instructionsFile = new File(targetDir, "README_INSTRUCTIONS.txt");
            try (FileWriter writer = new FileWriter(instructionsFile)) {
                writer.write("=========================================================\r\n");
                writer.write("   ASTRA EDR AGENT — TARGET LAPTOP DEPLOYMENT KIT       \r\n");
                writer.write("=========================================================\r\n\r\n");
                writer.write("Configured Central C2 URL: " + serverUrl + "\r\n");
                writer.write("Target Device Hostname   : " + targetHostname + "\r\n\r\n");
                writer.write("HOW TO INSTALL ON TARGET LAPTOP:\r\n");
                writer.write("1. Plug this USB drive into the target Windows laptop.\r\n");
                writer.write("2. Open this folder: " + dirName + "\r\n");
                writer.write("3. Right-click 'Deploy-Target-Agent.bat' and click 'Run as Administrator'.\r\n");
                writer.write("   -> This automatically configures and starts the AstraEDR background service.\r\n\r\n");
                writer.write("HOW TO RUN IN QUICK INTERACTIVE / DEBUG MODE:\r\n");
                writer.write("- Right-click 'Start-Target-Agent-Interactive.bat' and click 'Run as Administrator'.\r\n\r\n");
                writer.write("HOW TO UNINSTALL FROM TARGET LAPTOP:\r\n");
                writer.write("- Right-click 'Uninstall-Target-Agent.bat' and click 'Run as Administrator'.\r\n");
                writer.write("=========================================================\r\n");
            }

            log.info("Successfully provisioned complete USB agent deployment bundle at {}", targetDir.getAbsolutePath());
            return true;

        } catch (Exception e) {
            log.error("Failed to deploy agent to USB drive {}: {}", drivePath, e.getMessage(), e);
            return false;
        }
    }

    private File findProjectRoot() {
        File current = new File(".").getAbsoluteFile();
        if (new File(current, "windows-agent").exists() || new File(current, "Install-Astra.ps1").exists()) {
            return current;
        }
        if (new File(current, "..").exists()) {
            File parent = new File(current, "..").getAbsoluteFile();
            if (new File(parent, "windows-agent").exists() || new File(parent, "Install-Astra.ps1").exists()) {
                return parent;
            }
        }
        return current;
    }

    private File findFile(File root, String... relativePaths) {
        for (String rel : relativePaths) {
            File candidate = new File(root, rel);
            if (candidate.exists()) {
                return candidate;
            }
            // Check direct cwd
            File direct = new File(rel);
            if (direct.exists()) {
                return direct;
            }
        }
        return null;
    }

    private void copyIfExists(File src, File dest) {
        if (src != null && src.exists()) {
            try {
                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Staged file to USB: {}", dest.getName());
            } catch (Exception e) {
                log.warn("Could not copy file {} to {}: {}", src.getAbsolutePath(), dest.getAbsolutePath(), e.getMessage());
            }
        }
    }
}
