package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefenderMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.defender.enabled:true}")
    private boolean defenderMonitorEnabled = true;

    // Thread-safe state tracking
    private final AtomicBoolean wasRtpDisabled = new AtomicBoolean(false);
    private final Set<String> knownReportedThreats = ConcurrentHashMap.newKeySet();

    private static final String RTP_STATUS_CMD =
            "try { (Get-MpComputerStatus).RealTimeProtectionEnabled } catch { }";

    private static final String ACTIVE_THREATS_CMD =
            "try { Get-MpThreat | Select-Object -ExpandProperty ThreatName } catch { }";

    @PostConstruct
    public void init() {
        if (!defenderMonitorEnabled) {
            log.info("[DEFENDER-MONITOR] Defender monitor is disabled via configuration.");
            return;
        }

        try {
            // Baseline initial Real-Time Protection state
            CommandResult rtpResult = CommandRunner.runPowerShellWithResult(RTP_STATUS_CMD, 5);
            if (rtpResult.isSuccess() && !rtpResult.getStdout().isBlank()) {
                String val = rtpResult.getStdout().trim();
                if ("False".equalsIgnoreCase(val)) {
                    wasRtpDisabled.set(true);
                    log.info("[DEFENDER-MONITOR] Baseline state: Real-Time Protection is currently DISABLED.");
                } else if ("True".equalsIgnoreCase(val)) {
                    wasRtpDisabled.set(false);
                    log.info("[DEFENDER-MONITOR] Baseline state: Real-Time Protection is currently ENABLED.");
                }
            }

            // Baseline existing threats so historical entries do not trigger false new alerts on startup
            CommandResult threatResult = CommandRunner.runPowerShellWithResult(ACTIVE_THREATS_CMD, 5);
            if (threatResult.isSuccess() && !threatResult.getStdout().isBlank()) {
                String[] existingThreats = threatResult.getStdout().split("\\r?\\n");
                for (String t : existingThreats) {
                    String clean = t.trim();
                    if (!clean.isEmpty()) {
                        knownReportedThreats.add(clean);
                    }
                }
            }
            log.info("[DEFENDER-MONITOR] Initialized Defender baseline with {} existing historical threats.",
                    knownReportedThreats.size());

        } catch (Exception e) {
            log.warn("[DEFENDER-MONITOR] Could not initialize Defender baseline: {}", e.getMessage());
        }
    }

    /**
     * Periodic scan verifying Real-Time Protection state and newly detected threats.
     */
    @Scheduled(fixedRateString = "${agent.monitors.defender-rate:${agent.monitors.rate:1500}}")
    public void check() {
        if (!defenderMonitorEnabled) {
            return;
        }

        try {
            checkRealTimeProtection();
            checkActiveThreats();
        } catch (Exception e) {
            log.error("[DEFENDER-MONITOR] Error during Defender status check", e);
        }
    }

    /**
     * Checks Microsoft Defender Real-Time Protection status.
     */
    private void checkRealTimeProtection() {
        CommandResult result = CommandRunner.runPowerShellWithResult(RTP_STATUS_CMD, 5);

        if (!result.isSuccess() || result.getStdout().isBlank()) {
            log.debug("[DEFENDER-MONITOR] Could not query RTP status (exitCode={})", result.getExitCode());
            return;
        }

        String output = result.getStdout().trim();

        // Interpret status safely (True = Enabled / False = Disabled)
        Boolean isCurrentlyEnabled = null;
        if ("True".equalsIgnoreCase(output)) {
            isCurrentlyEnabled = true;
        } else if ("False".equalsIgnoreCase(output)) {
            isCurrentlyEnabled = false;
        }

        if (isCurrentlyEnabled == null) {
            log.debug("[DEFENDER-MONITOR] Unknown or unexpected RTP output: '{}'", output);
            return;
        }

        if (!isCurrentlyEnabled) {
            // State transition: Enabled -> Disabled
            if (wasRtpDisabled.compareAndSet(false, true)) {
                log.warn("[DEFENDER-MONITOR] [THREAT] Microsoft Defender Real-Time Protection is DISABLED!");
                try {
                    dispatcher.dispatch(
                            "AntivirusDisabled",
                            "Windows Defender Real-Time Protection has been turned OFF."
                    );
                } catch (Exception ex) {
                    log.error("[DEFENDER-MONITOR] Failed to dispatch AntivirusDisabled threat event", ex);
                }
            }
        } else {
            // State transition: Disabled -> Enabled
            if (wasRtpDisabled.compareAndSet(true, false)) {
                log.info("[DEFENDER-MONITOR] [RECOVERY] Microsoft Defender Real-Time Protection has been RE-ENABLED.");
            }
        }
    }

    /**
     * Checks for newly detected Microsoft Defender threats and dispatches alerts.
     */
    private void checkActiveThreats() {
        CommandResult result = CommandRunner.runPowerShellWithResult(ACTIVE_THREATS_CMD, 5);

        if (!result.isSuccess()) {
            return;
        }

        String output = result.getStdout().trim();
        Set<String> currentThreats = new HashSet<>();

        if (!output.isEmpty()) {
            String[] lines = output.split("\\r?\\n");
            for (String line : lines) {
                String cleanThreat = line.trim();
                if (cleanThreat.isEmpty()) {
                    continue;
                }

                currentThreats.add(cleanThreat);

                // If this is a newly observed threat that was not in the baseline / previously alerted
                if (knownReportedThreats.add(cleanThreat)) {
                    log.warn("[DEFENDER-MONITOR] [THREAT] Microsoft Defender detected new threat: {}", cleanThreat);
                    try {
                        dispatcher.dispatch(
                                "SecurityCenterAlert",
                                "Defender detected malicious threat: " + cleanThreat
                        );
                    } catch (Exception ex) {
                        knownReportedThreats.remove(cleanThreat);
                        log.error("[DEFENDER-MONITOR] Failed to dispatch SecurityCenterAlert for {}", cleanThreat, ex);
                    }
                }
            }
        }

        // Clean up resolved threats so if the same threat name re-appears later, it can alert again
        knownReportedThreats.retainAll(currentThreats);
    }
}
