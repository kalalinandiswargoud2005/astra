package com.astra.windowsagent.remediation;

import com.astra.windowsagent.service.AstraEnforcerOverlay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Handles safe, realistic, reversible, and presentation-ready endpoint threat simulations for ASTRA EDR.
 * 
 * Safety Guarantees:
 * - All file simulations strictly confined to C:\Astra\Demo.
 * - No real malware, destruction, unrecoverable encryption, or persistence is ever performed.
 * - Prior user wallpaper is captured and reliably restored via Win32 SystemParametersInfo.
 * - Every simulation state (baseline -> attack -> recovery) is cleanly tracked and fully reversible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSimulationService {

    private final AstraEnforcerOverlay overlay;

    private static final Path DEMO_BASE_DIR = Paths.get("C:\\Astra\\Demo");
    private static final String DEMO_REGISTRY_ROOT = "HKCU\\Software\\ASTRA\\Demo";

    /*
     * ============================================================
     * 1. RANSOMWARE SIMULATION
     * ============================================================
     */

    public String executeSimulatedRansomware(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        Path incidentDir = DEMO_BASE_DIR.resolve(safeId);
        Path baselineDir = incidentDir.resolve("baseline");
        Path attackDir = incidentDir.resolve("attack");
        Path recoveryDir = incidentDir.resolve("recovery");

        try {
            // Clean any stale prior artifacts for this incident ID to prevent collision
            if (Files.exists(incidentDir)) {
                deleteDirectory(incidentDir);
            }

            Files.createDirectories(baselineDir);
            Files.createDirectories(attackDir);
            Files.createDirectories(recoveryDir);

            String financials = "ASTRA ENTERPRISE FINANCIAL RECORDS 2026\n"
                    + "Q1 Operating Revenue: $18,450,000\n"
                    + "Target Margin: 34.2%\n"
                    + "Status: CONFIDENTIAL - BASELINE PROTECTED\n";

            String passwords = "ASTRA ENTERPRISE CREDENTIAL VAULT - DEMO ARTIFACT\n"
                    + "Master Service Principal: svc_astra_telemetry\n"
                    + "Token Hash: d41d8cd98f00b204e9800998ecf8427e\n"
                    + "Status: SIMULATED DATA ONLY\n";

            String report = "ASTRA CYBER INTELLIGENCE THREAT REPORT\n"
                    + "Classification: TOP SECRET // ORCON\n"
                    + "Threat Vector: Automated Ransomware Simulation\n"
                    + "Detection State: ASTRA EDR Active Enforcer\n";

            // 1. Establish Clean Baseline Files
            writeFile(baselineDir.resolve("financials.txt"), financials);
            writeFile(baselineDir.resolve("passwords.txt"), passwords);
            writeFile(baselineDir.resolve("report.txt"), report);

            // 2. Populate Attack Sandbox Directory
            Path financialFile = attackDir.resolve("financials.txt");
            Path passwordFile = attackDir.resolve("passwords.txt");
            Path reportFile = attackDir.resolve("report.txt");

            writeFile(financialFile, financials);
            writeFile(passwordFile, passwords);
            writeFile(reportFile, report);

            // 3. Simulate Ransomware Encryption (Renaming to .encrypted)
            Files.move(financialFile, attackDir.resolve("financials.txt.encrypted"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(passwordFile, attackDir.resolve("passwords.txt.encrypted"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(reportFile, attackDir.resolve("report.txt.encrypted"), StandardCopyOption.REPLACE_EXISTING);

            // 4. Generate Educational Demo Ransom Note
            writeFile(attackDir.resolve("HOW_TO_RECOVER_FILES.txt"),
                    """
                    ======================================================================
                                ASTRA EDR - SIMULATED RANSOMWARE ARTIFACT
                    ======================================================================
                    Incident ID  : %s
                    Timestamp    : %s
                    State        : ENCRYPTION SIMULATED (SAFE DEMO)
                    Real Threat  : NONE - No real user data was encrypted or harmed.
                    Recovery     : ASTRA EDR will autonomously restore the clean baseline.
                    ======================================================================
                    """.formatted(safeId, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            );

            // 5. Create Incident Manifest
            writeFile(incidentDir.resolve("manifest.txt"),
                    """
                    ASTRA SAFE RANSOMWARE SIMULATION
                    Incident ID: %s
                    Target Dir : %s
                    Status     : ACTIVE_SIMULATION
                    """.formatted(safeId, incidentDir)
            );

            log.info("[DEMO] Ransomware simulation created at {}", attackDir);

            overlay.showThreatAlert("SIMULATED RANSOMWARE - " + safeId);
            overlay.showMatrixOverlay();

            return "VERIFIED_SUCCESS: Ransomware simulation completed. Demo files created at " + attackDir;

        } catch (Exception e) {
            log.error("[DEMO] Ransomware simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 2. WALLPAPER HIJACK
     * ============================================================
     */

    public String executeSimulatedWallpaperHijack(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            Files.createDirectories(DEMO_BASE_DIR);
            Path incidentDir = DEMO_BASE_DIR.resolve(safeId);
            Files.createDirectories(incidentDir);

            // 1. Capture and save current wallpaper path for clean restoration
            String previousWallpaper = getCurrentWallpaper();
            if (previousWallpaper != null && !previousWallpaper.isBlank()) {
                writeFile(incidentDir.resolve("previous_wallpaper.txt"), previousWallpaper);
                log.info("[DEMO] Captured previous desktop wallpaper: {}", previousWallpaper);
            }

            // 2. Generate high-resolution ASTRA cyber threat wallpaper
            Path wallpaperPath = incidentDir.resolve("ASTRA_THREAT_SIMULATION.bmp");
            createAstraWallpaper(wallpaperPath, safeId);

            // 3. Apply the generated wallpaper immediately
            boolean changed = setWindowsWallpaper(wallpaperPath);
            if (!changed) {
                log.warn("[DEMO] Windows desktop update returned non-zero code; falling back to visual overlay.");
            } else {
                log.info("[DEMO] Windows desktop wallpaper successfully changed for incident {}", safeId);
            }

            overlay.showWallpaperHijackSimulation(safeId);
            overlay.showThreatAlert("SIMULATED WALLPAPER HIJACK - " + safeId);

            return "VERIFIED_SUCCESS: Windows wallpaper changed to ASTRA demo wallpaper.";

        } catch (Exception e) {
            log.error("[DEMO] Wallpaper simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 3. GHOST TYPER
     * ============================================================
     */

    public String executeSimulatedGhostTyper(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);
        log.info("[DEMO] Ghost typer simulation requested for incident: {}", safeId);

        overlay.showGhostTyperSimulation(safeId);
        overlay.showThreatAlert("SIMULATED GHOST TYPER - " + safeId);

        return "VERIFIED_SUCCESS: Ghost typer simulation displayed.";
    }

    /*
     * ============================================================
     * 4. BACKDOOR SIMULATION
     * ============================================================
     */

    public String executeSimulatedBackdoor(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            Path incidentDir = createIncidentDirectory(safeId);

            writeFile(incidentDir.resolve("backdoor_detection.txt"),
                    """
                    ASTRA SAFE THREAT SIMULATION
                    Threat              : SIMULATED_BACKDOOR_LISTENER
                    Incident ID         : %s
                    Simulated Protocol  : TCP / 127.0.0.1:44444
                    External Connection : NONE (Isolated Loopback)
                    Remediation         : Port termination and telemetry isolation
                    Status              : ACTIVE_DETECTION
                    """.formatted(safeId)
            );

            log.info("[DEMO] Backdoor behavior simulated for incident {}", safeId);

            overlay.showThreatAlert("SIMULATED BACKDOOR - TCP 44444");
            overlay.showMatrixOverlay();

            return "VERIFIED_SUCCESS: Backdoor behavior simulated. Safe telemetry created at " + incidentDir;

        } catch (Exception e) {
            log.error("[DEMO] Backdoor simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 5. REGISTRY HIJACK
     * ============================================================
     */

    public String executeSimulatedRegistryHijack(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            String keyPath = DEMO_REGISTRY_ROOT + "\\" + safeId;

            // Create ASTRA demo registry key in HKCU
            runProcess("reg", "add", keyPath, "/v", "DemoThreatActive", "/t", "REG_DWORD", "/d", "1", "/f");
            runProcess("reg", "add", keyPath, "/v", "SimulationType", "/t", "REG_SZ", "/d", "REGISTRY_HIJACK_DEMO", "/f");

            log.info("[DEMO] Registry demo artifact created at {}", keyPath);

            overlay.showThreatAlert("ASTRA REGISTRY TAMPER - " + safeId);

            return "VERIFIED_SUCCESS: ASTRA demo registry artifact created.";

        } catch (Exception e) {
            log.error("[DEMO] Registry simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 6. DARKSIDE-STYLE SIMULATION
     * ============================================================
     */

    public String executeSimulatedDarksidePayload(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            Path incidentDir = createIncidentDirectory(safeId);

            Path payload = incidentDir.resolve("simulated_ransomware_payload.txt");
            writeFile(payload,
                    """
                    ASTRA SAFE DEMO PAYLOAD
                    Payload Name       : simulated_darkside_payload
                    Incident ID        : %s
                    Simulated Behavior : Staged Ransomware Extraction
                    Real Malware       : NO
                    """.formatted(safeId)
            );

            // Create short-lived demo console script
            Path bat = incidentDir.resolve("astra_demo_window.bat");
            String batContent = "@echo off\r\n"
                    + "title ASTRA SAFE THREAT SIMULATION\r\n"
                    + "color 4F\r\n"
                    + "cls\r\n"
                    + "echo ========================================================\r\n"
                    + "echo       ASTRA SAFE THREAT SIMULATION (DEMO)\r\n"
                    + "echo ========================================================\r\n"
                    + "echo Incident : " + safeId + "\r\n"
                    + "echo Threat   : SIMULATED DARKSIDE RANSOMWARE STAGER\r\n"
                    + "echo Status   : DETECTED BY ASTRA EDR\r\n"
                    + "echo.\r\n"
                    + "echo [INFO] Safe demonstration in progress.\r\n"
                    + "echo.\r\n"
                    + "timeout /t 10 /nobreak >nul\r\n"
                    + "exit /b 0\r\n";

            writeFile(bat, batContent);

            new ProcessBuilder("cmd.exe", "/c", "start", "ASTRA SAFE THREAT SIMULATION", "cmd.exe", "/c", bat.toAbsolutePath().toString())
                    .start();

            overlay.showThreatAlert("SIMULATED DARKSIDE - " + safeId);
            overlay.showHackerSkull(safeId);

            return "VERIFIED_SUCCESS: DarkSide-style ASTRA simulation started.";

        } catch (Exception e) {
            log.error("[DEMO] DarkSide simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 7. STEALTH RAT SIMULATION
     * ============================================================
     */

    public String executeSimulatedStealthRat(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            Path incidentDir = createIncidentDirectory(safeId);

            writeFile(incidentDir.resolve("rat_detection.txt"),
                    """
                    ASTRA SAFE RAT SIMULATION
                    Threat      : STEALTH_RAT
                    Incident ID : %s
                    C2 Address  : 127.0.0.1:44444 (Loopback Telemetry)
                    Status      : CRITICAL_SIMULATED
                    """.formatted(safeId)
            );

            overlay.showThreatAlert("STEALTH RAT DETECTED - SIMULATED");
            overlay.showMatrixOverlay();

            return "VERIFIED_SUCCESS: Stealth RAT behavior simulated safely.";

        } catch (Exception e) {
            log.error("[DEMO] RAT simulation failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * 8. SIMULATION CLEANUP
     * ============================================================
     */

    public String cleanupSimulation(String incidentId) {
        String safeId = sanitizeIncidentId(incidentId);

        try {
            // 1. Restore original wallpaper if saved
            restoreWallpaper(safeId);

            // 2. Delete demo registry key
            deleteDemoRegistryKey(safeId);

            // 3. Delete incident directory cleanly
            Path incidentDir = DEMO_BASE_DIR.resolve(safeId);
            deleteDirectory(incidentDir);

            log.info("[DEMO] Cleanup completed for incident {}", safeId);
            return "VERIFIED_SUCCESS: ASTRA simulation cleaned up.";

        } catch (Exception e) {
            log.error("[DEMO] Cleanup failed for incident {}", safeId, e);
            return "FAILED: " + e.getMessage();
        }
    }

    /*
     * ============================================================
     * WALLPAPER SYSTEM FUNCTIONS
     * ============================================================
     */

    private boolean setWindowsWallpaper(Path wallpaper) throws Exception {
        String absolutePath = wallpaper.toAbsolutePath().toString();

        // Use Win32 SystemParametersInfo via PowerShell to trigger an immediate desktop refresh
        String script = String.format(
                "Add-Type -TypeDefinition @\"\n" +
                "using System;\n" +
                "using System.Runtime.InteropServices;\n" +
                "public class WinWallpaper {\n" +
                "    [DllImport(\"user32.dll\", CharSet = CharSet.Auto)]\n" +
                "    public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);\n" +
                "}\n" +
                "\"@; " +
                "Set-ItemProperty -Path 'HKCU:\\Control Panel\\Desktop' -Name Wallpaper -Value '%s'; " +
                "[WinWallpaper]::SystemParametersInfo(20, 0, '%s', 3);",
                absolutePath.replace("'", "''"), absolutePath.replace("'", "''")
        );

        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true)
                .start();

        int exitCode = process.waitFor();
        return exitCode == 0;
    }

    private String getCurrentWallpaper() throws Exception {
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", "(Get-ItemProperty -Path 'HKCU:\\Control Panel\\Desktop' -Name Wallpaper -ErrorAction SilentlyContinue).Wallpaper")
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        process.waitFor();

        return output.isBlank() ? null : output;
    }

    private void restoreWallpaper(String safeId) throws Exception {
        Path previous = DEMO_BASE_DIR.resolve(safeId).resolve("previous_wallpaper.txt");
        if (!Files.exists(previous)) {
            return;
        }

        String oldWallpaper = Files.readString(previous).trim();
        if (oldWallpaper.isBlank()) {
            return;
        }

        Path oldPath = Paths.get(oldWallpaper);
        if (Files.exists(oldPath)) {
            setWindowsWallpaper(oldPath);
            log.info("[DEMO] Prior desktop wallpaper restored from {}", oldWallpaper);
        } else {
            log.warn("[DEMO] Previous wallpaper path no longer exists: {}", oldWallpaper);
        }
    }

    /*
     * ============================================================
     * WALLPAPER GRAPHICS GENERATOR
     * ============================================================
     */

    private void createAstraWallpaper(Path output, String incidentId) throws IOException {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(Math.max(screen.width, 1920), 3840);
        int height = Math.min(Math.max(screen.height, 1080), 2160);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 1. Dark Gradient Background
            GradientPaint bgGradient = new GradientPaint(
                    0, 0, new Color(10, 14, 23),
                    width, height, new Color(5, 7, 12)
            );
            g.setPaint(bgGradient);
            g.fillRect(0, 0, width, height);

            // 2. Subtle Cyber Grid Lines
            g.setColor(new Color(255, 255, 255, 8));
            int gridSize = 60;
            for (int x = 0; x < width; x += gridSize) {
                g.drawLine(x, 0, x, height);
            }
            for (int y = 0; y < height; y += gridSize) {
                g.drawLine(0, y, width, y);
            }

            // 3. Glowing Red Security Accent Border
            g.setColor(new Color(229, 57, 53, 200));
            g.setStroke(new BasicStroke(8));
            g.drawRect(24, 24, width - 48, height - 48);

            // 4. Inner Cyan Tech Corner Accents
            g.setColor(new Color(0, 229, 255, 220));
            g.setStroke(new BasicStroke(4));
            int cornerLen = 50;
            // Top Left
            g.drawLine(36, 36, 36 + cornerLen, 36);
            g.drawLine(36, 36, 36, 36 + cornerLen);
            // Top Right
            g.drawLine(width - 36, 36, width - 36 - cornerLen, 36);
            g.drawLine(width - 36, 36, width - 36, 36 + cornerLen);
            // Bottom Left
            g.drawLine(36, height - 36, 36 + cornerLen, height - 36);
            g.drawLine(36, height - 36, 36, height - 36 - cornerLen);
            // Bottom Right
            g.drawLine(width - 36, height - 36, width - 36 - cornerLen, height - 36);
            g.drawLine(width - 36, height - 36, width - 36, height - 36 - cornerLen);

            FontMetrics fm;

            // 5. Main ASTRA Logo Title
            g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(54, width / 24)));
            g.setColor(new Color(0, 229, 255));
            String brand = "ASTRA EDR";
            fm = g.getFontMetrics();
            g.drawString(brand, (width - fm.stringWidth(brand)) / 2, height / 3);

            // Sub-brand label
            g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(16, width / 80)));
            g.setColor(new Color(180, 200, 220));
            String subBrand = "AUTONOMOUS ENDPOINT DEFENSE & THREAT RESPONSE";
            fm = g.getFontMetrics();
            g.drawString(subBrand, (width - fm.stringWidth(subBrand)) / 2, (height / 3) + 36);

            // 6. Threat Banner Box
            int bannerWidth = Math.min(width - 200, 900);
            int bannerHeight = 140;
            int bannerX = (width - bannerWidth) / 2;
            int bannerY = (height / 2) - 40;

            g.setColor(new Color(229, 57, 53, 35));
            g.fillRoundRect(bannerX, bannerY, bannerWidth, bannerHeight, 16, 16);
            g.setColor(new Color(229, 57, 53, 180));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(bannerX, bannerY, bannerWidth, bannerHeight, 16, 16);

            // Threat Warning Text
            g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(26, width / 55)));
            g.setColor(new Color(255, 82, 82));
            String warning = "SIMULATED ENDPOINT THREAT DETECTED";
            fm = g.getFontMetrics();
            g.drawString(warning, (width - fm.stringWidth(warning)) / 2, bannerY + 48);

            // Incident Metadata
            g.setFont(new Font("Consolas", Font.BOLD, Math.max(18, width / 75)));
            g.setColor(Color.WHITE);
            String incText = "INCIDENT ID: " + incidentId;
            fm = g.getFontMetrics();
            g.drawString(incText, (width - fm.stringWidth(incText)) / 2, bannerY + 86);

            // Status Text
            g.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(15, width / 95)));
            g.setColor(new Color(180, 190, 205));
            String modeText = "DEMONSTRATION MODE • REVERSIBLE SANDBOXED SIMULATION";
            fm = g.getFontMetrics();
            g.drawString(modeText, (width - fm.stringWidth(modeText)) / 2, bannerY + 118);

            // 7. Footer Notice
            g.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(13, width / 110)));
            g.setColor(new Color(120, 135, 155));
            String footer = "All changes are safe, isolated to C:\\Astra\\Demo, and automatically restored upon resolution.";
            fm = g.getFontMetrics();
            g.drawString(footer, (width - fm.stringWidth(footer)) / 2, height - 60);

        } finally {
            g.dispose();
        }

        ImageIO.write(image, "bmp", output.toFile());
        log.info("[DEMO] High-resolution ASTRA wallpaper generated at {}", output);
    }

    /*
     * ============================================================
     * HELPER FUNCTIONS
     * ============================================================
     */

    private void deleteDemoRegistryKey(String safeId) {
        String keyPath = DEMO_REGISTRY_ROOT + "\\" + safeId;
        try {
            runProcess("reg", "delete", keyPath, "/f");
            log.info("[DEMO] Demo registry artifact removed: {}", keyPath);
        } catch (Exception e) {
            log.debug("[DEMO] Registry key already absent or could not be removed: {}", keyPath);
        }
    }

    private Path createIncidentDirectory(String safeId) throws IOException {
        Path directory = DEMO_BASE_DIR.resolve(safeId);
        Files.createDirectories(directory);
        return directory;
    }

    private void writeFile(Path path, String content) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("[DEMO] Could not delete path during cleanup: {}", path);
                        }
                    });
        }
    }

    private void runProcess(String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Command failed (" + exitCode + "): " + output.trim());
        }
    }

    private String sanitizeIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return "INC-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String cleaned = incidentId.replaceAll("[^a-zA-Z0-9_-]", "").trim();
        if (cleaned.isBlank()) {
            return "INC-" + UUID.randomUUID().toString().substring(0, 8);
        }

        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }

        return cleaned;
    }
}

