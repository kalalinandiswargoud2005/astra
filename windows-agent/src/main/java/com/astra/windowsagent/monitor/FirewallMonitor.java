package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
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
public class FirewallMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.firewall.enabled:true}")
    private boolean firewallMonitorEnabled = true;

    // Thread-safe state tracking
    private final AtomicBoolean wasFirewallDisabled = new AtomicBoolean(false);
    private final Map<String, Boolean> lastProfileStates = new ConcurrentHashMap<>();

    private static final String POWERSHELL_FIREWALL_CMD =
            "Get-NetFirewallProfile | Format-Table -HideTableHeaders Name, Enabled";

    /**
     * Periodically monitors Domain, Private, and Public Windows Firewall profiles.
     */
    @Scheduled(fixedRateString = "${agent.monitors.firewall-rate:${agent.monitors.rate:1500}}")
    public void check() {
        if (!firewallMonitorEnabled) {
            return;
        }

        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(POWERSHELL_FIREWALL_CMD, 5);

            if (!result.isSuccess() || result.getStdout().isBlank()) {
                log.debug("[FIREWALL-MONITOR] PowerShell command failed or returned empty stdout (exitCode={})",
                        result.getExitCode());
                return;
            }

            Map<String, Boolean> currentProfiles = parseFirewallProfiles(result.getStdout());

            // Ensure valid profile telemetry was parsed before evaluating state
            if (currentProfiles.isEmpty()) {
                log.debug("[FIREWALL-MONITOR] No valid firewall profiles parsed from output: {}", result.getStdout());
                return;
            }

            lastProfileStates.putAll(currentProfiles);

            List<String> disabledProfiles = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : currentProfiles.entrySet()) {
                if (Boolean.FALSE.equals(entry.getValue())) {
                    disabledProfiles.add(entry.getKey());
                }
            }

            boolean hasDisabledProfile = !disabledProfiles.isEmpty();

            if (hasDisabledProfile) {
                // Trigger alert on state transition to disabled
                if (wasFirewallDisabled.compareAndSet(false, true)) {
                    String disabledListStr = String.join(", ", disabledProfiles);
                    log.warn("[FIREWALL-MONITOR] [THREAT] Windows Firewall profile(s) disabled: [{}]", disabledListStr);

                    try {
                        dispatcher.dispatch(
                                "FirewallDisabled",
                                "Windows Firewall profile(s) disabled: [" + disabledListStr + "]"
                        );
                    } catch (Exception dispatchEx) {
                        log.error("[FIREWALL-MONITOR] Failed to dispatch FirewallDisabled threat event", dispatchEx);
                    }
                }
            } else {
                // Trigger recovery when all profiles return to enabled
                if (wasFirewallDisabled.compareAndSet(true, false)) {
                    log.info("[FIREWALL-MONITOR] [RECOVERY] All Windows Firewall profiles restored to ENABLED (Domain, Private, Public).");
                }
            }

        } catch (Exception e) {
            log.error("[FIREWALL-MONITOR] Error during firewall status check", e);
        }
    }

    /**
     * Safely parses PowerShell output into a structured profile-to-state map.
     */
    private Map<String, Boolean> parseFirewallProfiles(String output) {
        Map<String, Boolean> profileMap = new HashMap<>();

        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] tokens = trimmed.split("\\s+");
            if (tokens.length >= 2) {
                String profileName = tokens[0].trim();
                String stateValue = tokens[1].trim().toLowerCase(Locale.ROOT);

                boolean isEnabled = "true".equalsIgnoreCase(stateValue)
                        || "1".equals(stateValue)
                        || "on".equalsIgnoreCase(stateValue);

                profileMap.put(profileName, isEnabled);
            }
        }

        return profileMap;
    }
}
