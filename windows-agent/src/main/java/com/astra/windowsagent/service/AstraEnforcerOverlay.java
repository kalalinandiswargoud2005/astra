package com.astra.windowsagent.service;

import com.astra.windowsagent.dto.AstraOverlayEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
public class AstraEnforcerOverlay {

    private JFrame matrixFrame;

    @Autowired(required = false)
    private OverlayIpcService ipcService;

    // ==========================================
    // IPC Dispatch Methods
    // ==========================================

    public void showThreatAlert(String threatName) {
        showThreatAlert(threatName, "INC-2026-" + System.currentTimeMillis() % 10000, "CRITICAL");
    }

    public void showThreatAlert(String threatName, String incidentId, String severity) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.THREAT_ALERT)
                    .target(threatName)
                    .incidentId(incidentId)
                    .severity(severity != null ? severity : "CRITICAL")
                    .details("Autonomous threat detection triggered on endpoint")
                    .build());
        }

        if (!GraphicsEnvironment.isHeadless()) {
            renderThreatAlertGui(threatName, incidentId, severity);
        } else {
            log.info("[SESSION-0/HEADLESS] Dispatched THREAT_ALERT overlay event via local IPC.");
        }
    }

    public void showMatrixOverlay() {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SHOW_MATRIX_OVERLAY)
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderMatrixOverlayGui();
        }
    }

    public void hideMatrixOverlay() {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.CLEAR_MATRIX)
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderHideMatrixGui();
        }
    }

    public void showWallpaperHijackSimulation(String incidentId) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.WALLPAPER_HIJACK_SIMULATION)
                    .incidentId(incidentId)
                    .target("Desktop Configuration")
                    .details("Simulated wallpaper modification blocked by ASTRA EDR")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderWallpaperHijackGui(incidentId);
        }
    }

    public void showGhostTyperSimulation(String incidentId) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.GHOST_TYPER_SIMULATION)
                    .incidentId(incidentId)
                    .target("Interactive User Session")
                    .details("Simulated unauthorized keystroke injection contained")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderGhostTyperGui(incidentId);
        }
    }

    public void showImmediateContainment(String threatName, String action, String status) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.IMMEDIATE_CONTAINMENT)
                    .target(threatName)
                    .details(action + " -> " + status)
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderContainmentGui(threatName, action, status);
        }
    }

    public void showRecoveryStep(int stepNum, int totalSteps, String title, String status) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.RECOVERY_STEP)
                    .stepNumber(stepNum)
                    .totalSteps(totalSteps)
                    .target(title)
                    .details(status)
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderRecoveryStepGui(stepNum, totalSteps, title, status);
        }
    }

    public void showFinalResolution(String threatName, String message) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.FINAL_RESOLUTION)
                    .target(threatName)
                    .details(message)
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderFinalResolutionGui(threatName, message);
        }
    }

    public void showSafeTestEnforcement(String threatName, String details) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SAFE_TEST_ENFORCEMENT)
                    .commandType("SHOW_TEST_ENFORCEMENT")
                    .target(threatName != null ? threatName : "Test Security Event")
                    .details(details != null ? details : "SAFE TEST RESPONSE RECEIVED")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderSafeTestEnforcementGui(threatName, details);
        }
    }

    // ==========================================
    // Interactive Swing GUI Rendering Routines
    // ==========================================

    public void renderThreatAlertGui(String threatName) {
        renderThreatAlertGui(threatName, "INC-2026-" + System.currentTimeMillis() % 10000, "CRITICAL");
    }

    public void renderThreatAlertGui(String threatName, String incidentId, String severity) {
        showCornerToast(
                "🚨 ASTRA EDR • THREAT DETECTED",
                threatName != null ? threatName : "Malicious Activity Detected",
                "Severity: " + (severity != null ? severity : "CRITICAL") + " • Containment Active",
                "Incident: " + (incidentId != null ? incidentId : "INC-ALERT"),
                new Color(255, 65, 65),
                new Color(22, 10, 15),
                8000,
                null,
                null
        );
    }

    /**
     * Renders a sleek, modern, non-intrusive bottom-right corner chat/toast notification on the target laptop screen.
     */
    public void showCornerToast(String badgeText, String titleText, int autoCloseMs, Color accentColor) {
        showCornerToast(badgeText, titleText, "ASTRA Autonomous Security Agent", "Telemetry & Endpoint Engine", accentColor, new Color(10, 15, 25), autoCloseMs, null, null);
    }

    public void showCornerToast(
            String badgeText,
            String titleText,
            String subText,
            String extraDetails,
            Color accentColor,
            Color bgColor,
            int autoCloseMs,
            Integer progressValue,
            Integer progressMax
    ) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();

                GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
                Rectangle screenBounds = gc.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

                int cardWidth = 420;
                int cardHeight = (progressValue != null) ? 170 : 145;

                int x = screenBounds.x + screenBounds.width - insets.right - cardWidth - 20;
                int y = screenBounds.y + screenBounds.height - insets.bottom - cardHeight - 20;

                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.setSize(cardWidth, cardHeight);
                frame.setLocation(x, y);
                frame.setAlwaysOnTop(true);
                frame.setFocusableWindowState(false); // Non-intrusive: never steals keyboard focus

                JPanel mainPanel = new JPanel(new BorderLayout());
                mainPanel.setBackground(bgColor);
                mainPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(accentColor, 2),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)
                ));

                // 1. Top Header Bar
                JPanel topBar = new JPanel(new BorderLayout(8, 0));
                topBar.setOpaque(false);

                JLabel titleLabel = new JLabel(badgeText);
                titleLabel.setFont(new Font("Consolas", Font.BOLD, 13));
                titleLabel.setForeground(accentColor);
                topBar.add(titleLabel, BorderLayout.WEST);

                JPanel topRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                topRightPanel.setOpaque(false);

                JButton closeBtn = new JButton("✕");
                closeBtn.setFont(new Font("Consolas", Font.BOLD, 12));
                closeBtn.setForeground(new Color(180, 180, 180));
                closeBtn.setBackground(new Color(0, 0, 0, 0));
                closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                closeBtn.setContentAreaFilled(false);
                closeBtn.setFocusPainted(false);
                closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                closeBtn.addActionListener(e -> frame.dispose());
                topRightPanel.add(closeBtn);

                topBar.add(topRightPanel, BorderLayout.EAST);
                mainPanel.add(topBar, BorderLayout.NORTH);

                // 2. Center Content Area
                JPanel centerPanel = new JPanel(new GridLayout(progressValue != null ? 3 : 2, 1, 0, 4));
                centerPanel.setOpaque(false);
                centerPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));

                JLabel nameLabel = new JLabel(titleText);
                nameLabel.setFont(new Font("Consolas", Font.BOLD, 14));
                nameLabel.setForeground(Color.WHITE);
                centerPanel.add(nameLabel);

                JLabel detailLabel = new JLabel(subText);
                detailLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
                detailLabel.setForeground(new Color(200, 210, 225));
                centerPanel.add(detailLabel);

                if (progressValue != null && progressMax != null) {
                    JProgressBar pBar = new JProgressBar(0, progressMax);
                    pBar.setValue(progressValue);
                    pBar.setStringPainted(true);
                    pBar.setString("Progress: Step " + progressValue + " of " + progressMax);
                    pBar.setFont(new Font("Consolas", Font.BOLD, 11));
                    pBar.setForeground(accentColor);
                    pBar.setBackground(new Color(25, 30, 40));
                    pBar.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 90), 1));
                    centerPanel.add(pBar);
                }

                mainPanel.add(centerPanel, BorderLayout.CENTER);

                // 3. Footer info
                JPanel footerPanel = new JPanel(new BorderLayout());
                footerPanel.setOpaque(false);

                String hostName = System.getenv("COMPUTERNAME") != null ? System.getenv("COMPUTERNAME") : "Endpoint";
                JLabel footerLabel = new JLabel("ASTRA EDR • Host: " + hostName + " • " + (extraDetails != null ? extraDetails : "Live Protection"));
                footerLabel.setFont(new Font("Consolas", Font.ITALIC, 10));
                footerLabel.setForeground(new Color(130, 145, 165));
                footerPanel.add(footerLabel, BorderLayout.WEST);

                mainPanel.add(footerPanel, BorderLayout.SOUTH);

                // Dismiss on click anywhere
                mainPanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        frame.dispose();
                    }
                });

                frame.add(mainPanel);
                frame.setVisible(true);
                frame.toFront();

                Timer autoClose = new Timer(autoCloseMs > 0 ? autoCloseMs : 7000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

            } catch (Exception e) {
                log.error("Failed to render corner toast notification GUI", e);
            }
        });
    }

    public void renderMatrixOverlayGui() {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            if (matrixFrame != null && matrixFrame.isVisible()) {
                matrixFrame.toFront();
                matrixFrame.requestFocus();
                return;
            }
            try {
                matrixFrame = new JFrame("ASTRA EDR MATRIX CONTAINMENT HUD");
                matrixFrame.setUndecorated(true);
                matrixFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                matrixFrame.setAlwaysOnTop(true);
                matrixFrame.setAutoRequestFocus(true);
                matrixFrame.setFocusableWindowState(true);
                try {
                    matrixFrame.setBackground(new Color(5, 10, 18, 230));
                } catch (Exception ignored) {
                    matrixFrame.setBackground(new Color(5, 10, 18));
                }

                JPanel panel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int w = getWidth();
                        int h = getHeight();

                        // Solid dark background
                        g2.setColor(new Color(5, 10, 18, 230));
                        g2.fillRect(0, 0, w, h);

                        // Top warning HUD banner
                        g2.setColor(new Color(0, 255, 128));
                        g2.setFont(new Font("Consolas", Font.BOLD, 22));
                        String title = "⚡ [ ASTRA EDR • MATRIX CYBER SECURITY HUD — LIVE STREAM ] ⚡";
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(title, (w - fm.stringWidth(title)) / 2, 60);

                        // Matrix rain simulation
                        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                        for (int i = 0; i < 280; i++) {
                            int rx = (int) (Math.random() * w);
                            int ry = (int) (Math.random() * h);
                            char c = (char) (Math.random() * 94 + 33);
                            g2.setColor(new Color(0, 255, 70, (int)(Math.random() * 180 + 75)));
                            g2.drawString(String.valueOf(c), rx, ry);
                        }

                        // Bottom dismissal instructions
                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dismissMsg = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS OVERLAY ]";
                        g2.drawString(dismissMsg, (w - g2.getFontMetrics().stringWidth(dismissMsg)) / 2, h - 40);
                    }
                };
                panel.setOpaque(true);

                panel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        renderHideMatrixGui();
                    }
                });

                matrixFrame.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            renderHideMatrixGui();
                        }
                    }
                });

                matrixFrame.add(panel);
                matrixFrame.setVisible(true);
                matrixFrame.toFront();
                matrixFrame.requestFocus();

                Timer repaintTimer = new Timer(50, e -> {
                    if (matrixFrame != null && matrixFrame.isVisible()) {
                        panel.repaint();
                    }
                });
                repaintTimer.start();

                // Auto-close matrix after 20 seconds so desktop is never trapped
                Timer autoClose = new Timer(20000, e -> renderHideMatrixGui());
                autoClose.setRepeats(false);
                autoClose.start();

                matrixFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });

            } catch (Exception e) {
                log.error("Failed to render matrix overlay GUI", e);
            }
        });
    }

    public void renderWallpaperHijackGui(String incidentId) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — WALLPAPER HIJACK CONTAINMENT");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                JPanel panel = new JPanel() {
                    private float phase = 0f;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        int w = getWidth();
                        int h = getHeight();

                        phase += 0.05f;

                        // Deep purple-magenta dark gradient background
                        g2.setColor(new Color(15, 6, 25));
                        g2.fillRect(0, 0, w, h);

                        // Animated magenta scanlines
                        g2.setColor(new Color(255, 60, 200, 35));
                        for (int y = 0; y < h; y += 12) {
                            g2.drawLine(0, y, w, y);
                        }

                        // Thick glowing border
                        int glowAlpha = (int) (150 + 80 * Math.sin(phase));
                        g2.setColor(new Color(255, 60, 200, Math.min(255, glowAlpha)));
                        g2.setStroke(new BasicStroke(8));
                        g2.drawRect(15, 15, w - 30, h - 30);

                        // Top warning banner
                        g2.setColor(new Color(255, 100, 220));
                        g2.setFont(new Font("Consolas", Font.BOLD, 26));
                        String topBanner = "🖼️  [ ASTRA EDR • SUSPICIOUS DESKTOP WALLPAPER TAMPER INTERCEPTED ]  🖼️";
                        FontMetrics fmTop = g2.getFontMetrics();
                        g2.drawString(topBanner, (w - fmTop.stringWidth(topBanner)) / 2, 70);

                        // Central Card
                        int cw = Math.min(780, w - 60);
                        int ch = 360;
                        int cx = (w - cw) / 2;
                        int cy = (h - ch) / 2;

                        g2.setColor(new Color(30, 12, 45, 240));
                        g2.fillRect(cx, cy, cw, ch);
                        g2.setColor(new Color(255, 80, 220));
                        g2.setStroke(new BasicStroke(3));
                        g2.drawRect(cx, cy, cw, ch);

                        g2.setFont(new Font("Consolas", Font.BOLD, 18));
                        g2.setColor(new Color(255, 180, 240));
                        g2.drawString("DETECTION : Unauthorized Desktop Configuration Tamper", cx + 30, cy + 50);
                        g2.drawString("INCIDENT  : " + (incidentId != null ? incidentId : "INC-WALLPAPER-TAMPER"), cx + 30, cy + 90);
                        g2.drawString("TARGET    : Desktop Wallpaper & Shell Registry (Active)", cx + 30, cy + 130);

                        g2.setColor(new Color(0, 255, 170));
                        g2.drawString("PROTECTION: Autonomous Containment Active — Desktop Preserved", cx + 30, cy + 185);
                        g2.drawString("STATUS    : ✓ Unauthorized Wallpaper Modification BLOCKED", cx + 30, cy + 225);
                        g2.drawString("VERIFIED  : Clean baseline locked by ASTRA Autonomous EDR", cx + 30, cy + 265);

                        // Dismiss banner
                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dis = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS OVERLAY ]";
                        g2.drawString(dis, (w - g2.getFontMetrics().stringWidth(dis)) / 2, h - 45);
                    }
                };
                panel.setOpaque(true);

                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { frame.dispose(); }
                });
                frame.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) frame.dispose();
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                Timer repaintTimer = new Timer(40, e -> {
                    if (frame.isVisible()) panel.repaint();
                });
                repaintTimer.start();

                Timer autoClose = new Timer(15000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render wallpaper hijack GUI", e);
            }
        });
    }

    public void renderGhostTyperGui(String incidentId) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — GHOST-TYPER INJECTION HUD");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                final String[] terminalLines = new String[]{
                        "[*] ASTRA EDR INTERCEPTOR ONLINE",
                        "[!] HEURISTIC ALERT: Rapid Synthetic Keystroke Injection Detected",
                        "[!] TARGET PID: cmd.exe [Interactive Shell Hook Attempt]",
                        "[!] INCIDENT ID: " + (incidentId != null ? incidentId : "INC-GHOST-TYPER-007"),
                        "----------------------------------------------------------------",
                        "> powershell -NoProfile -ExecutionPolicy Bypass -Command \"Invoke-Payload\"",
                        "> [INTERCEPTED] Keyboard Hook Disabled by Autonomous Defense",
                        "> [CONTAINMENT] Freezing unauthorized input stream...",
                        "> [ISOLATING] Malicious sub-process isolated & neutralized.",
                        "----------------------------------------------------------------",
                        "[✓] STATUS: THREAT CONTAINED & TERMINATED IN 14ms"
                };

                final int[] visibleChars = new int[]{0};

                JPanel panel = new JPanel() {
                    private float phase = 0f;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        int w = getWidth();
                        int h = getHeight();

                        phase += 0.08f;

                        // Dark cyan-black console background
                        g2.setColor(new Color(6, 15, 22));
                        g2.fillRect(0, 0, w, h);

                        // Cyan scanlines
                        g2.setColor(new Color(0, 240, 255, 25));
                        for (int y = 0; y < h; y += 8) {
                            g2.drawLine(0, y, w, y);
                        }

                        // Glowing border
                        g2.setColor(new Color(0, 220, 255));
                        g2.setStroke(new BasicStroke(6));
                        g2.drawRect(12, 12, w - 24, h - 24);

                        // Top warning HUD
                        g2.setColor(new Color(0, 240, 255));
                        g2.setFont(new Font("Consolas", Font.BOLD, 24));
                        String title = "⚡ [ ASTRA EDR • GHOST-TYPER KEYSTROKE INJECTION CONTAINMENT HUD ] ⚡";
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(title, (w - fm.stringWidth(title)) / 2, 65);

                        // Terminal Box
                        int tw = Math.min(900, w - 80);
                        int th = Math.min(520, h - 180);
                        int tx = (w - tw) / 2;
                        int ty = 100;

                        g2.setColor(new Color(10, 22, 32, 245));
                        g2.fillRect(tx, ty, tw, th);
                        g2.setColor(new Color(0, 180, 220));
                        g2.setStroke(new BasicStroke(2));
                        g2.drawRect(tx, ty, tw, th);

                        // Terminal Text Output
                        g2.setFont(new Font("Consolas", Font.BOLD, 16));
                        int lineY = ty + 35;
                        int totalCharsAvailable = visibleChars[0];
                        int count = 0;

                        for (String line : terminalLines) {
                            if (totalCharsAvailable <= 0) break;
                            int len = Math.min(line.length(), totalCharsAvailable);
                            String toDraw = line.substring(0, len);

                            if (line.startsWith("[!]")) {
                                g2.setColor(new Color(255, 80, 80));
                            } else if (line.startsWith("> [INTERCEPTED]") || line.startsWith("> [CONTAINMENT]") || line.startsWith("> [ISOLATING]")) {
                                g2.setColor(new Color(255, 200, 50));
                            } else if (line.startsWith("[✓]")) {
                                g2.setColor(new Color(0, 255, 150));
                            } else if (line.startsWith(">")) {
                                g2.setColor(new Color(140, 220, 255));
                            } else {
                                g2.setColor(new Color(0, 240, 255));
                            }

                            g2.drawString(toDraw, tx + 25, lineY);
                            lineY += 32;
                            totalCharsAvailable -= line.length();
                        }

                        // Blinking cursor
                        if ((int) (phase * 2) % 2 == 0) {
                            g2.setColor(new Color(0, 255, 170));
                            g2.fillRect(tx + 25, lineY - 14, 12, 18);
                        }

                        // Dismiss note
                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dis = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS OVERLAY ]";
                        g2.drawString(dis, (w - g2.getFontMetrics().stringWidth(dis)) / 2, h - 40);
                    }
                };
                panel.setOpaque(true);

                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { frame.dispose(); }
                });
                frame.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) frame.dispose();
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                // Typer ticker
                Timer typerTimer = new Timer(30, e -> {
                    visibleChars[0] += 3;
                    if (frame.isVisible()) panel.repaint();
                });
                typerTimer.start();

                Timer autoClose = new Timer(15000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        typerTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render ghost-typer GUI", e);
            }
        });
    }

    public void renderRecoveryStepGui(int stepNum, int totalSteps, String title, String status) {
        int maxSteps = totalSteps > 0 ? totalSteps : 5;
        showCornerToast(
                String.format("🔄 ASTRA RECOVERY • STEP %d/%d", stepNum, maxSteps),
                title != null ? title : "Remediation Playbook Step",
                "Status: SUCCESS • Verified On Endpoint",
                "Playbook Progress",
                new Color(0, 230, 140),
                new Color(10, 25, 20),
                8000,
                stepNum,
                maxSteps
        );
    }

    public void renderContainmentGui(String threatName, String action, String status) {
        showCornerToast(
                "⚡ ASTRA EDR • IMMEDIATE CONTAINMENT",
                threatName != null ? threatName : "Active Threat Vector",
                "Action: " + (action != null ? action : "Isolating Host & Freezing Process"),
                "Autonomous Containment Active",
                new Color(255, 165, 0),
                new Color(25, 18, 10),
                7000,
                null,
                null
        );
    }

    public void renderFinalResolutionGui(String threatName, String message) {
        renderHideMatrixGui();
        showCornerToast(
                "✅ ASTRA EDR • THREAT RESOLVED",
                threatName != null ? threatName : "Target Endpoint",
                "Status: Clean Baseline Confirmed • Secured",
                "All Playbooks Complete",
                new Color(0, 220, 255),
                new Color(10, 22, 35),
                9000,
                null,
                null
        );
    }

    public void renderSafeTestEnforcementGui(String threatName, String details) {
        showCornerToast(
                "🛡️ ASTRA EDR • SAFE TEST EVENT",
                threatName != null ? threatName : "Safe Verification Event",
                "Details: " + (details != null ? details : "Command executed successfully"),
                "Enforcement Pipeline Verified",
                new Color(0, 210, 255),
                new Color(12, 20, 32),
                6000,
                null,
                null
        );
    }

    public void showHackerSkull(String incidentId) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SHOW_HACKER_SKULL)
                    .incidentId(incidentId)
                    .target("Hacker Skull Ransomware Overlay")
                    .details("Critical Ransomware ASCII Skull Breach")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderHackerSkullGui(incidentId);
        }
    }

    public void renderHackerSkullGui(String incidentId) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — HACKER WALLPAPER HIJACK");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                JPanel panel = new JPanel() {
                    private float phase = 0f;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                        int w = getWidth();
                        int h = getHeight();
                        phase += 0.08f;

                        // 1. Solid pitch black background
                        g2.setColor(new Color(6, 6, 12));
                        g2.fillRect(0, 0, w, h);

                        // 2. Animated red hazard scanlines
                        g2.setColor(new Color(255, 30, 30, 45));
                        for (int y = 0; y < h; y += 8) {
                            g2.drawLine(0, y, w, y);
                        }

                        // 3. Thick glowing red border
                        int glowAlpha = (int) (180 + 75 * Math.sin(phase));
                        g2.setColor(new Color(255, 30, 30, Math.min(255, glowAlpha)));
                        g2.setStroke(new BasicStroke(10));
                        g2.drawRect(12, 12, w - 24, h - 24);

                        // 4. Top Warning Header Banner
                        g2.setColor(new Color(255, 50, 50));
                        g2.setFont(new Font("Consolas", Font.BOLD, 26));
                        String topBanner = "☠️  [ CRITICAL RANSOMWARE VECTOR DETECTED — ASTRA LIVE SANDBOX DEMO ]  ☠️";
                        FontMetrics fmTop = g2.getFontMetrics();
                        g2.drawString(topBanner, (w - fmTop.stringWidth(topBanner)) / 2, 70);

                        // 5. Draw Graphical Skull Silhouette Vectors
                        int cx = w / 2;
                        int cy = h / 2 - 40;

                        // Skull Cranium
                        g2.setColor(new Color(0, 255, 120, 230));
                        g2.fillOval(cx - 120, cy - 140, 240, 210);

                        // Skull Jaw
                        g2.fillRect(cx - 65, cy + 40, 130, 70);

                        // Skull Eye Sockets (Dark)
                        g2.setColor(new Color(6, 6, 12));
                        g2.fillOval(cx - 80, cy - 35, 55, 70);
                        g2.fillOval(cx + 25, cy - 35, 55, 70);

                        // Glowing Red Eye Pupils (Pulsing)
                        int eyeGlow = (int) (180 + 75 * Math.sin(phase * 2));
                        g2.setColor(new Color(255, 30, 30, Math.min(255, eyeGlow)));
                        g2.fillOval(cx - 58, cy - 10, 22, 22);
                        g2.fillOval(cx + 38, cy - 10, 22, 22);

                        // Nose Cavity
                        g2.setColor(new Color(6, 6, 12));
                        int[] xPoints = { cx, cx - 18, cx + 18 };
                        int[] yPoints = { cy + 25, cy + 55, cy + 55 };
                        g2.fillPolygon(xPoints, yPoints, 3);

                        // Teeth
                        g2.setColor(new Color(6, 6, 12));
                        g2.setStroke(new BasicStroke(4));
                        g2.drawLine(cx - 50, cy + 75, cx + 50, cy + 75);
                        for (int tx = cx - 40; tx <= cx + 40; tx += 20) {
                            g2.drawLine(tx, cy + 60, tx, cy + 95);
                        }

                        // 6. Threat Details & Instructions Banner
                        int textY = cy + 150;
                        g2.setColor(new Color(255, 70, 70));
                        g2.setFont(new Font("Consolas", Font.BOLD, 22));
                        String threatIdStr = "THREAT ID: " + (incidentId != null ? incidentId : "INC-DARKSIDE-09") + " [LIVE BREACH]";
                        FontMetrics fmThreat = g2.getFontMetrics();
                        g2.drawString(threatIdStr, (w - fmThreat.stringWidth(threatIdStr)) / 2, textY);

                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Consolas", Font.BOLD, 18));
                        String alertMsg = "SANDBOX ARTIFACTS ENCRYPTED (C:\\Astra\\Demo) — REMEDIATE VIA ASTRA RECOVERY PLAYBOOK";
                        FontMetrics fmAlert = g2.getFontMetrics();
                        g2.drawString(alertMsg, (w - fmAlert.stringWidth(alertMsg)) / 2, textY + 36);

                        g2.setColor(new Color(0, 230, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dismissMsg = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS OVERLAY ]";
                        FontMetrics fmDis = g2.getFontMetrics();
                        g2.drawString(dismissMsg, (w - fmDis.stringWidth(dismissMsg)) / 2, h - 45);
                    }
                };
                panel.setOpaque(true);

                panel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        frame.dispose();
                    }
                });

                frame.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            frame.dispose();
                        }
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                Timer repaintTimer = new Timer(40, e -> {
                    if (frame.isVisible()) panel.repaint();
                });
                repaintTimer.start();

                Timer autoClose = new Timer(20000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render hacker skull GUI", e);
            }
        });
    }

    public void showGlitchBreach(String incidentId) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SHOW_GLITCH_BREACH)
                    .incidentId(incidentId)
                    .target("Zero-Day Memory Glitch Violation")
                    .details("Heuristic Memory Stack Corruption Contained")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderGlitchBreachGui(incidentId);
        }
    }

    public void renderGlitchBreachGui(String incidentId) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — MEMORY CORRUPTION GLITCH");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                JPanel panel = new JPanel() {
                    private float phase = 0f;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                        int w = getWidth();
                        int h = getHeight();
                        phase += 0.1f;

                        // Solid dark background
                        g2.setColor(new Color(15, 5, 25));
                        g2.fillRect(0, 0, w, h);

                        // Simulated scanlines and memory glitch noise
                        g2.setColor(new Color(255, 0, 128, 90));
                        for (int y = 0; y < h; y += 10) {
                            g2.drawLine(0, y, w, y);
                        }

                        // Random glitch blocks
                        for (int i = 0; i < 15; i++) {
                            int gx = (int) (Math.random() * (w - 150));
                            int gy = (int) (Math.random() * (h - 30));
                            int gw = (int) (Math.random() * 200 + 50);
                            int gh = (int) (Math.random() * 20 + 5);
                            g2.setColor(new Color(0, 255, 255, (int)(Math.random() * 120 + 30)));
                            g2.fillRect(gx, gy, gw, gh);
                        }

                        // Glitch title with chromatic shift
                        String title = "⚡ [ ZERO-DAY MEMORY CORRUPTION & BUFFER INJECTION DETECTED ] ⚡";
                        Font titleFont = new Font("Consolas", Font.BOLD, 28);
                        g2.setFont(titleFont);
                        FontMetrics fm = g2.getFontMetrics();
                        int tx = (w - fm.stringWidth(title)) / 2;
                        int ty = h / 2 - 40;

                        // Red chromatic offset
                        g2.setColor(new Color(255, 0, 80, 200));
                        g2.drawString(title, tx + (int)(3 * Math.sin(phase)), ty + 2);

                        // Cyan chromatic offset
                        g2.setColor(new Color(0, 255, 255));
                        g2.drawString(title, tx, ty);

                        g2.setColor(new Color(255, 255, 0));
                        g2.setFont(new Font("Consolas", Font.BOLD, 18));
                        String sub1 = "HEURISTIC: HEAP_SPRAY_VIOLATION | INCIDENT: " + (incidentId != null ? incidentId : "INC-0DAY-MEMORY");
                        g2.drawString(sub1, (w - g2.getFontMetrics().stringWidth(sub1)) / 2, h / 2 + 15);

                        g2.setColor(new Color(0, 255, 140));
                        String sub2 = "ASTRA Autonomous Memory Guard: Stack integrity preserved. Injected buffer neutralized.";
                        g2.drawString(sub2, (w - g2.getFontMetrics().stringWidth(sub2)) / 2, h / 2 + 50);

                        // Simulated memory addresses
                        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                        g2.setColor(new Color(255, 120, 200, 180));
                        for (int j = 0; j < 6; j++) {
                            String hexLine = String.format("0x%08X: 90 90 CC CC E8 %02X %02X FF FF [CORRUPTED_STACK_DUMP]", 0x7FFE0000 + j * 0x10, (int)(Math.random()*255), (int)(Math.random()*255));
                            g2.drawString(hexLine, 40, h - 160 + j * 18);
                        }

                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dis = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS ]";
                        g2.drawString(dis, (w - g2.getFontMetrics().stringWidth(dis)) / 2, h - 35);
                    }
                };
                panel.setOpaque(true);
                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { frame.dispose(); }
                });
                frame.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) frame.dispose();
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                Timer repaintTimer = new Timer(40, e -> {
                    if (frame.isVisible()) panel.repaint();
                });
                repaintTimer.start();

                Timer autoClose = new Timer(15000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render glitch GUI", e);
            }
        });
    }

    public void showRadarBeacon(String incidentId) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SHOW_RADAR_BEACON)
                    .incidentId(incidentId)
                    .target("C2 Radar Beacon Intercept")
                    .details("Rogue TCP 44444 Socket Beaconing")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderRadarBeaconGui(incidentId);
        }
    }

    public void renderRadarBeaconGui(String incidentId) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — C2 RADAR INTERCEPT");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                JPanel panel = new JPanel() {
                    private double sweepAngle = 0.0;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                        int w = getWidth();
                        int h = getHeight();
                        sweepAngle += 0.05;

                        // Deep radar navy background
                        g2.setColor(new Color(5, 15, 24));
                        g2.fillRect(0, 0, w, h);

                        // Top warning banner
                        g2.setColor(new Color(0, 255, 140));
                        g2.setFont(new Font("Consolas", Font.BOLD, 24));
                        String title = "📡  [ ASTRA EDR • C2 RADAR TELEMETRY & BEACON INTERCEPT HUD ]  📡";
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(title, (w - fm.stringWidth(title)) / 2, 60);

                        // Radar Circle Dimensions
                        int cx = w / 2;
                        int cy = h / 2 + 10;
                        int radius = Math.min(w, h) / 2 - 90;

                        // Concentric range circles
                        g2.setColor(new Color(0, 255, 140, 60));
                        g2.setStroke(new BasicStroke(1.5f));
                        for (int r = radius / 4; r <= radius; r += radius / 4) {
                            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                        }

                        // Crosshairs
                        g2.drawLine(cx - radius, cy, cx + radius, cy);
                        g2.drawLine(cx, cy - radius, cx, cy + radius);

                        // Rotating Sonar Beam Sweep
                        int sweepX = (int) (cx + radius * Math.cos(sweepAngle));
                        int sweepY = (int) (cy + radius * Math.sin(sweepAngle));

                        g2.setColor(new Color(0, 255, 140, 220));
                        g2.setStroke(new BasicStroke(3));
                        g2.drawLine(cx, cy, sweepX, sweepY);

                        // Sweep fan gradient trail
                        for (int i = 1; i <= 30; i++) {
                            double trailAngle = sweepAngle - (i * 0.015);
                            int tx = (int) (cx + radius * Math.cos(trailAngle));
                            int ty = (int) (cy + radius * Math.sin(trailAngle));
                            g2.setColor(new Color(0, 255, 140, Math.max(0, 100 - i * 3)));
                            g2.drawLine(cx, cy, tx, ty);
                        }

                        // Intercepted C2 Blip on Loopback TCP 44444
                        int blipX = cx + (int) (radius * 0.65 * Math.cos(1.2));
                        int blipY = cy + (int) (radius * 0.65 * Math.sin(1.2));
                        int blipGlow = (int) (160 + 90 * Math.sin(sweepAngle * 3));

                        g2.setColor(new Color(255, 50, 50, Math.min(255, blipGlow)));
                        g2.fillOval(blipX - 10, blipY - 10, 20, 20);
                        g2.setColor(new Color(255, 255, 255));
                        g2.setFont(new Font("Consolas", Font.BOLD, 13));
                        g2.drawString("⚠️ TARGET BEACON: TCP 127.0.0.1:44444", blipX + 15, blipY + 5);

                        // Stats Telemetry Panel (Left)
                        g2.setColor(new Color(8, 28, 42, 220));
                        g2.fillRect(35, 90, 340, 220);
                        g2.setColor(new Color(0, 220, 255));
                        g2.setStroke(new BasicStroke(2));
                        g2.drawRect(35, 90, 340, 220);

                        g2.setFont(new Font("Consolas", Font.BOLD, 14));
                        g2.setColor(new Color(0, 240, 255));
                        g2.drawString("INTERCEPTED C2 TELEMETRY", 50, 120);
                        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                        g2.setColor(new Color(180, 230, 255));
                        g2.drawString("• Port Binding   : 127.0.0.1:44444", 50, 150);
                        g2.drawString("• Protocol       : TCP / Loopback RAT", 50, 175);
                        g2.drawString("• Beacon Interval: 3000ms Synthetic", 50, 200);
                        g2.drawString("• Incident Ref   : " + (incidentId != null ? incidentId : "INC-RADAR-44444"), 50, 225);
                        g2.setColor(new Color(0, 255, 140));
                        g2.drawString("• Autonomous EDR : PORT INTERCEPTED", 50, 255);
                        g2.drawString("• Remediation    : CLOSE_BACKDOOR READY", 50, 280);

                        // Dismiss note
                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dis = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS OVERLAY ]";
                        g2.drawString(dis, (w - g2.getFontMetrics().stringWidth(dis)) / 2, h - 35);
                    }
                };
                panel.setOpaque(true);
                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { frame.dispose(); }
                });
                frame.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) frame.dispose();
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                Timer repaintTimer = new Timer(40, e -> {
                    if (frame.isVisible()) panel.repaint();
                });
                repaintTimer.start();

                Timer autoClose = new Timer(15000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render radar GUI", e);
            }
        });
    }

    public void showHexShield(String target) {
        if (ipcService != null) {
            ipcService.publishEvent(AstraOverlayEvent.builder()
                    .type(AstraOverlayEvent.EventType.SHOW_HEX_SHIELD)
                    .target(target != null ? target : "Local Workstation")
                    .details("Hexagonal Defense Shield Level 5 Hardening")
                    .build());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            renderHexShieldGui(target);
        }
    }

    public void renderHexShieldGui(String target) {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
                JFrame frame = new JFrame("ASTRA EDR — HEXAGONAL DEFENSE SHIELD");
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setAlwaysOnTop(true);
                frame.setAutoRequestFocus(true);
                frame.setFocusableWindowState(true);

                JPanel panel = new JPanel() {
                    private float phase = 0f;
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                        int w = getWidth();
                        int h = getHeight();
                        phase += 0.06f;

                        // Deep dark blue-cyan security background
                        g2.setColor(new Color(6, 16, 32));
                        g2.fillRect(0, 0, w, h);

                        // Draw Hexagonal Matrix Grid across background
                        int hexSize = 55;
                        double hexH = Math.sqrt(3) * hexSize;
                        g2.setColor(new Color(0, 210, 255, 30));
                        g2.setStroke(new BasicStroke(1.2f));

                        for (int col = -1; col < w / (hexSize * 1.5) + 2; col++) {
                            for (int row = -1; row < h / hexH + 2; row++) {
                                double hx = col * hexSize * 1.5;
                                double hy = row * hexH + ((col % 2 == 0) ? 0 : hexH / 2);
                                drawHexagon(g2, (int) hx, (int) hy, hexSize - 4);
                            }
                        }

                        // Outer pulsing cyan containment barrier
                        int borderGlow = (int) (170 + 80 * Math.sin(phase));
                        g2.setColor(new Color(0, 220, 255, Math.min(255, borderGlow)));
                        g2.setStroke(new BasicStroke(8));
                        g2.drawRect(15, 15, w - 30, h - 30);

                        // Top Title Banner
                        g2.setColor(new Color(0, 240, 255));
                        g2.setFont(new Font("Consolas", Font.BOLD, 26));
                        String topBanner = "🛡️  [ ASTRA AUTONOMOUS CYBER DEFENSE SHIELD — LEVEL 5 ACTIVE ]  🛡️";
                        FontMetrics fmTop = g2.getFontMetrics();
                        g2.drawString(topBanner, (w - fmTop.stringWidth(topBanner)) / 2, 65);

                        // Central Defense Telemetry Card
                        int cw = Math.min(820, w - 60);
                        int ch = 400;
                        int cx = (w - cw) / 2;
                        int cy = (h - ch) / 2;

                        g2.setColor(new Color(10, 28, 55, 240));
                        g2.fillRect(cx, cy, cw, ch);
                        g2.setColor(new Color(0, 210, 255));
                        g2.setStroke(new BasicStroke(3));
                        g2.drawRect(cx, cy, cw, ch);

                        // Status rows
                        g2.setFont(new Font("Consolas", Font.BOLD, 18));
                        g2.setColor(new Color(0, 240, 255));
                        g2.drawString("SHIELD STATUS: 100% OPERATIONAL • LEVEL 5 HARDENED", cx + 35, cy + 45);

                        g2.setFont(new Font("Consolas", Font.PLAIN, 15));
                        g2.setColor(new Color(180, 235, 255));
                        g2.drawString("=================================================================", cx + 35, cy + 75);

                        g2.drawString("[✓] HOST FIREWALL PROFILE    : ENFORCED (ALL DOMAIN/PRIVATE/PUBLIC)", cx + 35, cy + 115);
                        g2.drawString("[✓] MICROSOFT DEFENDER RT    : ACTIVE & REAL-TIME INTERCEPT ON", cx + 35, cy + 155);
                        g2.drawString("[✓] INTEGRITY AUDIT AGENT    : 18 LOW-LATENCY PROBES VERIFIED", cx + 35, cy + 195);
                        g2.drawString("[✓] NETWORK ISOLATION MATRIX : DEFENSIVE BARRIER ENGAGED", cx + 35, cy + 235);
                        g2.drawString("[✓] TARGET WORKSTATION       : " + (target != null ? target : "Local Endpoint"), cx + 35, cy + 275);

                        g2.setFont(new Font("Consolas", Font.BOLD, 16));
                        g2.setColor(new Color(0, 255, 160));
                        g2.drawString("ALL PERIMETERS SECURE. SIMULATED ATTACK VECTORS CONTAINED.", cx + 35, cy + 340);

                        // Bottom dismissal instructions
                        g2.setColor(new Color(0, 220, 255));
                        g2.setFont(new Font("Consolas", Font.ITALIC, 14));
                        String dismissMsg = "[ PRESS ESC OR CLICK ANYWHERE TO DISMISS SHIELD HUD ]";
                        g2.drawString(dismissMsg, (w - g2.getFontMetrics().stringWidth(dismissMsg)) / 2, h - 45);
                    }

                    private void drawHexagon(Graphics2D g2, int cx, int cy, int size) {
                        int[] xPoints = new int[6];
                        int[] yPoints = new int[6];
                        for (int i = 0; i < 6; i++) {
                            double angle = Math.PI / 3 * i;
                            xPoints[i] = (int) (cx + size * Math.cos(angle));
                            yPoints[i] = (int) (cy + size * Math.sin(angle));
                        }
                        g2.drawPolygon(xPoints, yPoints, 6);
                    }
                };
                panel.setOpaque(true);

                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { frame.dispose(); }
                });
                frame.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) frame.dispose();
                    }
                });

                frame.add(panel);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();

                Timer repaintTimer = new Timer(40, e -> {
                    if (frame.isVisible()) panel.repaint();
                });
                repaintTimer.start();

                Timer autoClose = new Timer(15000, e -> frame.dispose());
                autoClose.setRepeats(false);
                autoClose.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        repaintTimer.stop();
                        autoClose.stop();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to render hex shield GUI", e);
            }
        });
    }

    public void renderHideMatrixGui() {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            if (matrixFrame != null) {
                matrixFrame.dispose();
                matrixFrame = null;
            }
        });
    }
}
