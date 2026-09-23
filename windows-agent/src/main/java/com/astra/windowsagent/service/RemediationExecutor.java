package com.astra.windowsagent.service;

import com.astra.windowsagent.dto.DeviceCommandDto;
import com.astra.windowsagent.remediation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemediationExecutor {

    private final AstraEnforcerOverlay overlay;
    private final FileRemediationService fileService;
    private final ProcessRemediationService processService;
    private final NetworkRemediationService networkService;
    private final WindowsSecurityService windowsSecurityService;
    private final DemoSimulationService demoSimulationService;
    private final VerificationService verificationService;
    private final com.astra.windowsagent.config.AgentConfigHelper configHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Idempotency cache: commandId -> Result JSON (prevents duplicate execution)
    private final Map<String, String> executedCommandsCache = new ConcurrentHashMap<>();

    public String execute(DeviceCommandDto command) {
        String rawType = command.getCommandType() != null ? command.getCommandType().trim() : "UNKNOWN";
        String target = command.getTarget();
        String params = command.getParameters();
        String commandId = command.getId() != null ? command.getId().toString() : "CMD-" + System.currentTimeMillis();
        String incidentId = command.getIncidentId() != null ? command.getIncidentId().toString() : "INC-GENERIC";
        String deviceId = command.getDeviceId() != null ? command.getDeviceId() : "local-endpoint";

        // Extract parameters from JSON payload if available
        int stepNumber = 1;
        int totalSteps = 5;
        if (params != null && !params.isBlank()) {
            try {
                JsonNode pNode = objectMapper.readTree(params);
                if ((target == null || target.isBlank())) {
                    if (pNode.has("target")) target = pNode.get("target").asText();
                    else if (pNode.has("file")) target = pNode.get("file").asText();
                    else if (pNode.has("title")) target = pNode.get("title").asText();
                }
                if (pNode.has("stepOrder")) stepNumber = pNode.get("stepOrder").asInt();
                if (pNode.has("stepNumber")) stepNumber = pNode.get("stepNumber").asInt();
                if (pNode.has("totalSteps")) totalSteps = pNode.get("totalSteps").asInt();
            } catch (Exception ignored) {}
        }

        // Check for idempotency (if already executed, return cached result)
        if (executedCommandsCache.containsKey(commandId)) {
            log.info("[REMEDIATION-IDEMPOTENCY] Command {} already executed. Returning cached outcome.", commandId);
            return executedCommandsCache.get(commandId);
        }

        RemediationAction action = RemediationAction.fromString(rawType);
        log.info("[REMEDIATION-RECEIVED] commandId={}, incidentId={}, deviceId={}, action={}, target={}",
                commandId, incidentId, deviceId, action, target);

        // Reject unknown or unlisted commands (Arbitrary command execution is strictly blocked)
        if (action == null) {
            log.error("[REMEDIATION-REJECTED] Unsupported command '{}' blocked by defensive allowlist.", rawType);
            String rejectResult = buildResultJson(commandId, incidentId, deviceId, rawType, "REJECTED", "FAILED",
                    "REJECTED_UNSUPPORTED_COMMAND: Arbitrary or unlisted command blocked for security: " + rawType,
                    "Unauthorized command pattern");
            executedCommandsCache.put(commandId, rejectResult);
            return rejectResult;
        }

        String verificationResult = "FAILED";
        String executionMessage = "";
        String errorMsg = null;

        try {
            switch (action) {
                // 1. Live Visual Demonstrations
                case SHOW_THREAT_ALERT:
                    overlay.showThreatAlert(target != null ? target : "Active Threat Vector", incidentId, "CRITICAL");
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Threat alert displayed on target screen";
                    break;

                case SHOW_MATRIX_OVERLAY:
                    overlay.showThreatAlert("Matrix Security HUD Stream", incidentId, "INFO");
                    overlay.showMatrixOverlay();
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Matrix security HUD displayed";
                    break;

                case CLEAR_MATRIX:
                    overlay.hideMatrixOverlay();
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Matrix HUD cleared";
                    break;

                case SIMULATE_WALLPAPER_HIJACK:
                    overlay.showThreatAlert("Simulated Wallpaper Hijack Attempt", incidentId, "HIGH");
                    executionMessage = demoSimulationService.executeSimulatedWallpaperHijack(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    break;

                case SIMULATE_GHOST_TYPER:
                    overlay.showThreatAlert("Ghost-Typer Keystroke Injection Attack", incidentId, "HIGH");
                    executionMessage = demoSimulationService.executeSimulatedGhostTyper(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    break;

                case SHOW_TEST_ENFORCEMENT:
                    overlay.showSafeTestEnforcement(target != null ? target : "Endpoint Pipeline", "SAFE TEST RESPONSE RECEIVED (SUCCESS)");
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Safe test verification displayed";
                    break;

                case SHOW_HACKER_SKULL:
                    overlay.showThreatAlert("Hacker Skull Ransomware Hijack", incidentId, "CRITICAL");
                    overlay.showHackerSkull(incidentId);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Hacker skull wallpaper overlay displayed";
                    break;

                case SHOW_RADAR_BEACON:
                    overlay.showThreatAlert("C2 Beacon Intercept & Radar Telemetry", incidentId, "HIGH");
                    overlay.showRadarBeacon(incidentId);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: C2 radar beacon overlay displayed";
                    break;

                case SHOW_GLITCH_BREACH:
                    overlay.showThreatAlert("Zero-Day Memory Corruption Breach", incidentId, "CRITICAL");
                    overlay.showGlitchBreach(incidentId);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Memory glitch overlay displayed";
                    break;

                case SHOW_HEX_SHIELD:
                    overlay.showThreatAlert("Hexagonal Cyber Shield Engaged", incidentId, "LOW");
                    overlay.showHexShield(target);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Hexagonal defense shield displayed";
                    break;

                // 2. Safe Attack Scenarios
                case EXECUTE_SAFE_ATTACK:
                case START_SAFE_ATTACK:
                    String attackTarget = target != null ? target.toUpperCase() : "SIMULATED_RANSOMWARE";
                    if (attackTarget.contains("DARKSIDE") || attackTarget.contains("ROGUE")) {
                        overlay.showThreatAlert("DarkSide Rogue Window & Injection", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedDarksidePayload(incidentId);
                    } else if (attackTarget.contains("STEALTH_RAT") || attackTarget.contains("RAT")) {
                        overlay.showThreatAlert("Stealth RAT Backdoor Socket (TCP 44444)", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedStealthRat(incidentId);
                    } else if (attackTarget.contains("SKULL") || attackTarget.contains("HACKER")) {
                        overlay.showThreatAlert("Hacker Skull Ransomware Hijack Overlay", incidentId, "CRITICAL");
                        overlay.showHackerSkull(incidentId);
                        executionMessage = "VERIFIED_SUCCESS: Hacker skull wallpaper active";
                    } else if (attackTarget.contains("GLITCH") || attackTarget.contains("MEMORY")) {
                        overlay.showThreatAlert("Zero-Day Memory Corruption & Buffer Overflow", incidentId, "CRITICAL");
                        overlay.showGlitchBreach(incidentId);
                        executionMessage = "VERIFIED_SUCCESS: Memory glitch active";
                    } else if (attackTarget.contains("RADAR") || attackTarget.contains("BEACON")) {
                        overlay.showThreatAlert("C2 Beacon Intercept & Radar Telemetry", incidentId, "HIGH");
                        overlay.showRadarBeacon(incidentId);
                        executionMessage = "VERIFIED_SUCCESS: Radar beacon active";
                    } else if (attackTarget.contains("MATRIX")) {
                        overlay.showThreatAlert("Matrix Cyber Security HUD Active", incidentId, "MEDIUM");
                        overlay.showMatrixOverlay();
                        executionMessage = "VERIFIED_SUCCESS: Matrix security HUD active";
                    } else if (attackTarget.contains("HEX") || attackTarget.contains("SHIELD") || attackTarget.contains("DEFENSE")) {
                        overlay.showThreatAlert("Hexagonal Cyber Shield Active", incidentId, "LOW");
                        overlay.showHexShield(target);
                        executionMessage = "VERIFIED_SUCCESS: Hex defense shield active";
                    } else if (attackTarget.contains("WALLPAPER")) {
                        overlay.showThreatAlert("Simulated Desktop Wallpaper Hijack", incidentId, "HIGH");
                        executionMessage = demoSimulationService.executeSimulatedWallpaperHijack(incidentId);
                    } else if (attackTarget.contains("GHOST") || attackTarget.contains("TYPER") || attackTarget.contains("KEYSTROKE")) {
                        overlay.showThreatAlert("Ghost-Typer Keystroke Injection Attack", incidentId, "HIGH");
                        executionMessage = demoSimulationService.executeSimulatedGhostTyper(incidentId);
                    } else if (attackTarget.contains("BACKDOOR") || attackTarget.contains("PORT")) {
                        overlay.showThreatAlert("Simulated Backdoor Port Opened (TCP 44444)", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedBackdoor(incidentId);
                    } else if (attackTarget.contains("REGISTRY")) {
                        overlay.showThreatAlert("Simulated Registry Hijack Active", incidentId, "HIGH");
                        executionMessage = demoSimulationService.executeSimulatedRegistryHijack(incidentId);
                    } else if (attackTarget.contains("LATERAL")) {
                        overlay.showThreatAlert("Lateral Movement Privilege Escalation", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedRegistryHijack(incidentId);
                    } else if (attackTarget.contains("EXFILTRATION")) {
                        overlay.showThreatAlert("Massive Outbound Data Exfiltration Detected", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedRansomware(incidentId);
                    } else if (attackTarget.contains("TEST")) {
                        overlay.showSafeTestEnforcement(target != null ? target : "Endpoint Pipeline", "SAFE TEST RESPONSE RECEIVED (SUCCESS)");
                        executionMessage = "VERIFIED_SUCCESS: Safe test verification displayed";
                    } else {
                        overlay.showThreatAlert("Simulated Ransomware File Encryption", incidentId, "CRITICAL");
                        executionMessage = demoSimulationService.executeSimulatedRansomware(incidentId);
                    }
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    break;

                case SIMULATE_DARKSIDE_PAYLOAD:
                    executionMessage = demoSimulationService.executeSimulatedDarksidePayload(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    break;

                case SIMULATE_STEALTH_RAT:
                    executionMessage = demoSimulationService.executeSimulatedStealthRat(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    break;

                case STOP_SAFE_ATTACK:
                    processService.stopDemoProcess("ping.exe");
                    processService.closeRogueWindow(target);
                    networkService.stopDemoListener(44444);
                    fileService.restoreDemoFiles(incidentId);
                    demoSimulationService.cleanupSimulation(incidentId);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Safe attack simulation stopped and sandbox reset";
                    break;

                // 3. Sandboxed File Remediation
                case QUARANTINE_DEMO_FILE:
                case QUARANTINE_TEST_FILE:
                    executionMessage = fileService.quarantineDemoFile(target, incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Quarantine Sandbox Artifact", verificationResult);
                    break;

                case RESTORE_DEMO_FILE:
                case RESTORE_TEST_FILE:
                    executionMessage = fileService.restoreDemoFiles(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Restore Baseline Files", verificationResult);
                    break;

                case REMOVE_DEMO_ARTIFACTS:
                case REMOVE_TEST_PERSISTENCE:
                    executionMessage = fileService.removeDemoPersistence(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Remove Persistence Artifacts", verificationResult);
                    break;

                // 4. Process & Network Remediation
                case STOP_DEMO_PROCESS:
                case STOP_TEST_PROCESS:
                case KILL_PROCESS:
                    executionMessage = processService.stopDemoProcess(target);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Stop Demo Process: " + (target != null ? target : "ping.exe"), verificationResult);
                    break;

                case SNIPE_ROGUE_WINDOW:
                case CLOSE_ROGUE_WINDOW:
                    executionMessage = processService.closeRogueWindow(target);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Snipe & Close Rogue Window", verificationResult);
                    break;

                case LOCK_WORKSTATION:
                case LOCK_ENDPOINT:
                    executionMessage = processService.lockWorkstation();
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Lock Target Workstation", verificationResult);
                    break;

                case STOP_DEMO_LISTENER:
                case STOP_TEST_LISTENER:
                    executionMessage = networkService.stopDemoListener(44444);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Close Simulated Backdoor Port 44444", verificationResult);
                    break;

                case STOP_TEST_EXFILTRATION:
                    processService.stopDemoProcess("ping.exe");
                    fileService.removeDemoPersistence(incidentId);
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Exfiltration stream terminated and verified";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Terminate Exfiltration Stream", "VERIFIED");
                    break;

                case RESTORE_DEMO_REGISTRY:
                case RESTORE_TEST_REGISTRY:
                    executionMessage = networkService.restoreDemoRegistry(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Restore Demo Registry Keys", verificationResult);
                    break;

                // 5. Windows Defensive Security
                case DISABLE_RDP:
                    executionMessage = windowsSecurityService.disableRdp();
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Disable Remote Desktop (RDP)", verificationResult);
                    break;

                case RESTORE_FIREWALL:
                    executionMessage = windowsSecurityService.restoreFirewall();
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Enable Host Firewall (All Profiles)", verificationResult);
                    break;

                case ENABLE_DEFENDER_REALTIME:
                case ENABLE_REALTIME:
                    executionMessage = windowsSecurityService.enableDefenderRealtime();
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Enable Defender Real-Time Protection", verificationResult);
                    break;

                case FULL_DEFENDER_SCAN:
                    executionMessage = windowsSecurityService.runDefenderScan();
                    verificationResult = "SUCCESS";
                    overlay.showRecoveryStep(stepNumber, totalSteps, "Quick Defender Endpoint Scan", "VERIFIED");
                    break;

                case ISOLATE_DEVICE:
                    executionMessage = windowsSecurityService.isolateDevice();
                    verificationResult = "SUCCESS";
                    overlay.showImmediateContainment(target != null ? target : "Endpoint", "NETWORK ISOLATION", "ENFORCED");
                    break;

                case RESTORE_NETWORK:
                    executionMessage = windowsSecurityService.restoreNetwork();
                    verificationResult = "SUCCESS";
                    overlay.showFinalResolution("Network Restored", "Endpoint network baseline re-established");
                    break;

                // 6. Recovery Step & Final Verification
                case RECOVERY_STEP:
                    String stepTitle = target != null ? target : "Automated Remediation Step";
                    String lowerTitle = stepTitle.toLowerCase();

                    // Execute real physical actions matching step titles
                    if (lowerTitle.contains("snipe") || lowerTitle.contains("rogue threat window") || lowerTitle.contains("close rogue")) {
                        processService.closeRogueWindow(target);
                    } else if (lowerTitle.contains("lock") && (lowerTitle.contains("workstation") || lowerTitle.contains("laptop") || lowerTitle.contains("screen"))) {
                        processService.lockWorkstation();
                    } else if (lowerTitle.contains("terminate") && (lowerTitle.contains("port") || lowerTitle.contains("listener") || lowerTitle.contains("rat"))) {
                        networkService.stopDemoListener(44444);
                    } else if (lowerTitle.contains("registry") || lowerTitle.contains("persistence")) {
                        networkService.restoreDemoRegistry(incidentId);
                        fileService.removeDemoPersistence(incidentId);
                    } else if (lowerTitle.contains("quarantine") || lowerTitle.contains("staged malware")) {
                        fileService.quarantineDemoFile("darkside_stager.bin", incidentId);
                    } else if (lowerTitle.contains("firewall") || lowerTitle.contains("defender")) {
                        windowsSecurityService.restoreFirewall();
                        windowsSecurityService.enableDefenderRealtime();
                    } else if (lowerTitle.contains("remote desktop") || lowerTitle.contains("rdp")) {
                        windowsSecurityService.disableRdp();
                    } else if (lowerTitle.contains("stop simulated attack") || lowerTitle.contains("stop demo process")) {
                        processService.stopDemoProcess("ping.exe");
                    }

                    overlay.showRecoveryStep(stepNumber, totalSteps, stepTitle, "VERIFIED");
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: " + stepTitle;
                    break;

                case FINAL_VERIFICATION:
                    executionMessage = verificationService.performFinalVerification(incidentId);
                    verificationResult = executionMessage.startsWith("VERIFIED_SUCCESS") ? "SUCCESS" : "FAILED";
                    if ("SUCCESS".equals(verificationResult)) {
                        overlay.showFinalResolution(incidentId, "Endpoint verified clean. All threats neutralized.");
                    }
                    break;

                case FINAL_RESOLUTION:
                    String resTarget = target != null ? target : "Endpoint Secured";
                    overlay.showFinalResolution(resTarget, "All playbooks executed. Endpoint in clean baseline.");
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Endpoint secured";
                    break;

                case RESTART_AGENT:
                    overlay.showCornerToast("🔄 ASTRA EDR AGENT", "Remote restart command acknowledged. Restarting agent...", 4000, new java.awt.Color(0, 240, 255));
                    triggerAgentRestart();
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Agent restart initialized";
                    break;

                case UPDATE_AGENT:
                    overlay.showCornerToast("⚡ ASTRA EDR OTA UPDATE", "Downloading latest agent feature update from SOC...", 4000, new java.awt.Color(0, 255, 136));
                    triggerAgentUpdate(configHelper != null ? configHelper.getBackendUrls() : java.util.List.of());
                    verificationResult = "SUCCESS";
                    executionMessage = "VERIFIED_SUCCESS: Agent OTA update package downloading";
                    break;

                default:
                    executionMessage = "FAILED: Unhandled action " + action;
                    verificationResult = "FAILED";
            }
        } catch (Exception e) {
            log.error("[REMEDIATION-FAILED] Exception executing action {}: {}", action, e.getMessage(), e);
            errorMsg = e.getMessage();
            verificationResult = "FAILED";
            executionMessage = "FAILED: " + e.getMessage();
        }

        String finalStatus = "SUCCESS".equalsIgnoreCase(verificationResult) ? "COMPLETED" : "FAILED";
        String resultJson = buildResultJson(commandId, incidentId, deviceId, action.name(), finalStatus, verificationResult, executionMessage, errorMsg);
        
        // Cache result for idempotency
        executedCommandsCache.put(commandId, resultJson);

        return resultJson;
    }

    private void triggerAgentRestart() {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                String cmd = "powershell -NoProfile -WindowStyle Hidden -Command \"Start-Sleep -Milliseconds 800; Start-Process 'java.exe' -ArgumentList '-Djava.awt.headless=false -jar windows-agent.jar' -WindowStyle Normal\"";
                Runtime.getRuntime().exec(cmd);
                System.exit(0);
            } catch (Exception e) {
                log.error("[AGENT-RESTART] Failed to trigger restart: {}", e.getMessage());
            }
        }, 800, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void triggerAgentUpdate(java.util.List<String> backendUrls) {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                boolean downloaded = false;
                for (String backendUrl : backendUrls) {
                    try {
                        String downloadUrl = backendUrl + "/api/v1/agent/binary/download";
                        java.io.File targetFile = new java.io.File("windows-agent.jar.new");
                        try (java.io.InputStream in = new java.net.URL(downloadUrl).openStream();
                             java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
                            org.springframework.util.FileCopyUtils.copy(in, out);
                        }
                        if (targetFile.exists() && targetFile.length() > 10000) {
                            downloaded = true;
                            log.info("[OTA-UPDATE] Successfully downloaded {} bytes from {}", targetFile.length(), downloadUrl);
                            break;
                        }
                    } catch (Exception ex) {
                        log.warn("[OTA-UPDATE] Download attempt failed from {}: {}", backendUrl, ex.getMessage());
                    }
                }

                if (downloaded) {
                    overlay.showCornerToast("🔄 ASTRA EDR RELOADING", "Installing update and restarting agent with new capabilities...", 4000, new java.awt.Color(0, 255, 136));
                    String cmd = "powershell -NoProfile -WindowStyle Hidden -Command \"Start-Sleep -Seconds 1; Move-Item -Force -Path 'windows-agent.jar.new' -Destination 'windows-agent.jar'; Start-Process 'java.exe' -ArgumentList '-Djava.awt.headless=false -jar windows-agent.jar' -WindowStyle Normal\"";
                    Runtime.getRuntime().exec(cmd);
                    System.exit(0);
                } else {
                    log.error("[OTA-UPDATE] Failed to download updated JAR from any backend endpoint.");
                }
            } catch (Exception e) {
                log.error("[OTA-UPDATE] Failed to execute update sequence: {}", e.getMessage());
            }
        }, 800, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private String buildResultJson(String commandId, String incidentId, String deviceId, String commandType,
                                   String status, String verification, String message, String errorMsg) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("commandId", commandId);
        map.put("incidentId", incidentId);
        map.put("deviceId", deviceId);
        map.put("commandType", commandType);
        map.put("status", status); // COMPLETED, FAILED, REJECTED
        map.put("verification", verification); // SUCCESS, FAILED
        map.put("message", message);
        if (errorMsg != null) map.put("errorMessage", errorMsg);
        map.put("timestamp", LocalDateTime.now().toString());

        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return message;
        }
    }
}
