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

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class RDPMonitor {

    public enum RdpState {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.rdp.enabled:true}")
    private boolean rdpMonitorEnabled = true;

    // Thread-safe state tracking
    private final AtomicReference<RdpState> lastKnownState = new AtomicReference<>(RdpState.UNKNOWN);

    private static final String RDP_QUERY_CMD =
            "try { (Get-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server' -Name 'fDenyTSConnections' -ErrorAction Stop).fDenyTSConnections } catch { }";

    @PostConstruct
    public void init() {
        if (!rdpMonitorEnabled) {
            log.info("[RDP-MONITOR] RDP monitor is disabled via configuration.");
            return;
        }

        RdpState initialState = queryRdpState();
        lastKnownState.set(initialState);
        log.info("[RDP-MONITOR] Initialized baseline RDP state: {}", initialState);
    }

    /**
     * Periodically monitors Windows Terminal Server RDP status.
     */
    @Scheduled(fixedRateString = "${agent.monitors.rdp-rate:${agent.monitors.rate:1500}}")
    public void check() {
        if (!rdpMonitorEnabled) {
            return;
        }

        try {
            RdpState currentState = queryRdpState();

            // Never process or change state if query returned UNKNOWN (PowerShell/registry error)
            if (currentState == RdpState.UNKNOWN) {
                log.debug("[RDP-MONITOR] Skipping check cycle; RDP state could not be determined.");
                return;
            }

            RdpState previousState = lastKnownState.getAndSet(currentState);

            if (previousState == RdpState.DISABLED && currentState == RdpState.ENABLED) {
                // Threat event: RDP was unexpectedly enabled
                log.warn("[RDP-MONITOR] [THREAT] Remote Desktop (RDP) was ENABLED on endpoint (fDenyTSConnections = 0)!");
                try {
                    dispatcher.dispatch(
                            "RDPEnabled",
                            "Remote Desktop (Terminal Services) was enabled unexpectedly (fDenyTSConnections = 0)"
                    );
                } catch (Exception ex) {
                    log.error("[RDP-MONITOR] Failed to dispatch RDPEnabled threat event", ex);
                }

            } else if (previousState == RdpState.ENABLED && currentState == RdpState.DISABLED) {
                // Recovery event: RDP was disabled
                log.info("[RDP-MONITOR] [RECOVERY] Remote Desktop (RDP) was DISABLED (fDenyTSConnections = 1).");

            } else if (previousState == RdpState.UNKNOWN) {
                log.info("[RDP-MONITOR] RDP state established after startup: {}", currentState);
            }

        } catch (Exception e) {
            log.error("[RDP-MONITOR] Error during RDP status check", e);
        }
    }

    /**
     * Queries the registry for the fDenyTSConnections value.
     * Returns:
     * - ENABLED if fDenyTSConnections == 0
     * - DISABLED if fDenyTSConnections == 1
     * - UNKNOWN if query failed, permission denied, or value missing
     */
    private RdpState queryRdpState() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(RDP_QUERY_CMD, 5);

            if (!result.isSuccess() || result.getStdout().isBlank()) {
                log.debug("[RDP-MONITOR] RDP registry query returned no output or failed (exitCode={})", result.getExitCode());
                return RdpState.UNKNOWN;
            }

            String output = result.getStdout().trim();

            if ("0".equals(output)) {
                return RdpState.ENABLED;
            } else if ("1".equals(output)) {
                return RdpState.DISABLED;
            } else {
                log.debug("[RDP-MONITOR] Unexpected fDenyTSConnections value: '{}'", output);
                return RdpState.UNKNOWN;
            }

        } catch (Exception e) {
            log.debug("[RDP-MONITOR] Failed to query RDP registry setting: {}", e.getMessage());
            return RdpState.UNKNOWN;
        }
    }
}
