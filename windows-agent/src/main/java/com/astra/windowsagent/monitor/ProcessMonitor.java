package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import com.astra.windowsagent.remediation.ProcessRemediationService;
import com.astra.windowsagent.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMonitor {

    private final ThreatDispatcher dispatcher;
    private final ProcessRemediationService processRemediationService;

    @Value("${astra.agent.autonomous-kill:true}")
    private boolean autonomousKill;

    /*
     * Processes and security audit tools flagged for demonstration,
     * forensic telemetry, or defensive containment.
     */
    private static final Set<String> SUSPICIOUS_PROCESSES =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(
                            "mimikatz",
                            "nc",
                            "nmap",
                            "netcat",
                            "wireshark",
                            "hydra",
                            "metasploit",
                            "msfconsole",
                            "ransomware_sim",
                            "attack_simulation",
                            "simulated_malware"
                    ))
            );

    /*
     * Thread-safe tracker to prevent alert storms while
     * the same process continues running.
     */
    private final Set<String> activeAlertedProcesses =
            ConcurrentHashMap.newKeySet();

    @Scheduled(
            fixedRateString =
                    "${agent.monitors.process-rate:${agent.monitors.rate:1000}}"
    )
    public void check() {
        try {
            String output = CommandRunner.runPowerShell(
                    "Get-Process | Select-Object -ExpandProperty Name"
            );

            if (output == null || output.isBlank()) {
                return;
            }

            Set<String> currentlyRunning = parseProcesses(output);

            for (String processName : currentlyRunning) {
                if (!isSuspicious(processName)) {
                    continue;
                }

                // Prevent repeating alert for ongoing process
                if (activeAlertedProcesses.contains(processName)) {
                    continue;
                }

                log.warn("[PROCESS-MONITOR] Suspicious process detected: {}", processName);

                try {
                    dispatcher.dispatch(
                            "SuspiciousProcess",
                            "Suspicious process detected: " + processName
                    );
                    activeAlertedProcesses.add(processName);
                } catch (Exception dispatchException) {
                    // Retry on next cycle if backend dispatch fails
                    log.error(
                            "[PROCESS-MONITOR] Failed to dispatch alert for {}",
                            processName,
                            dispatchException
                    );
                }

                // Autonomous local reflex containment (works even when offline / disconnected)
                if (autonomousKill) {
                    log.warn("[PROCESS-MONITOR] [AUTONOMOUS-REFLEX] Executing local zero-trust termination for: {}", processName);
                    try {
                        String killResult = processRemediationService.stopDemoProcess(processName);
                        log.info("[PROCESS-MONITOR] [AUTONOMOUS-RESULT] {}: {}", processName, killResult);
                    } catch (Exception killEx) {
                        log.error("[PROCESS-MONITOR] Autonomous kill failed for {}", processName, killEx);
                    }
                }
            }

            // Evict terminated processes
            activeAlertedProcesses.retainAll(currentlyRunning);

        } catch (Exception e) {
            log.error("[PROCESS-MONITOR] Process scan failed", e);
        }
    }

    /**
     * Matches process names against threat signatures, accounting for extensions (e.g. .exe).
     */
    private boolean isSuspicious(String processName) {
        if (SUSPICIOUS_PROCESSES.contains(processName)) {
            return true;
        }
        for (String signature : SUSPICIOUS_PROCESSES) {
            if (processName.startsWith(signature + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts PowerShell output into normalized, lowercase process names.
     */
    private Set<String> parseProcesses(String output) {
        Set<String> processes = new HashSet<>();
        for (String line : output.split("\\r?\\n")) {
            String process = line.trim().toLowerCase(Locale.ROOT);
            if (!process.isBlank()) {
                processes.add(process);
            }
        }
        return processes;
    }
}
