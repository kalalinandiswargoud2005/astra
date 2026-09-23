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

@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.network.enabled:true}")
    private boolean networkMonitorEnabled = true;

    // A simple mock list of known malicious IPs (C2 servers, cryptominers)
    private static final Set<String> MALICIOUS_IPS = Set.of(
            "185.158.24.120", "45.133.1.200", "192.3.2.1" // Example mock IPs
    );

    private final Set<String> reportedConnections = ConcurrentHashMap.newKeySet();

    private static final String POWERSHELL_NETSTAT_CMD =
            "Get-NetTCPConnection -State Established | Select-Object -Property LocalAddress, LocalPort, RemoteAddress, RemotePort, OwningProcess | Format-Table -HideTableHeaders";

    @Scheduled(fixedRateString = "${agent.monitors.network-rate:${agent.monitors.rate:15000}}")
    public void check() {
        if (!networkMonitorEnabled) return;

        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(POWERSHELL_NETSTAT_CMD, 5);
            if (!result.isSuccess() || result.getStdout().isBlank()) return;

            String[] lines = result.getStdout().split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 5) {
                    String remoteIp = parts[2].trim();
                    String pid = parts[4].trim();

                    if (MALICIOUS_IPS.contains(remoteIp)) {
                        String connectionId = remoteIp + ":" + pid;
                        if (!reportedConnections.contains(connectionId)) {
                            log.warn("[NETWORK-MONITOR] [THREAT] Malicious Outbound Connection Detected: PID {} to {}", pid, remoteIp);
                            dispatcher.dispatch("MaliciousConnection", "Process PID " + pid + " connected to malicious IP: " + remoteIp);
                            reportedConnections.add(connectionId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[NETWORK-MONITOR] Error checking network connections", e);
        }
    }
}
