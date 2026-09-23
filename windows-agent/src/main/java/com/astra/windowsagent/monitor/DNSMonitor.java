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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DNSMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.dns.enabled:true}")
    private boolean dnsMonitorEnabled = true;

    private static final String DNS_QUERY_CMD =
            "Get-DnsClientServerAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | " +
            "Where-Object { $_.ServerAddresses.Count -gt 0 } | Select-Object -ExpandProperty ServerAddresses";

    private final Set<String> baselineDnsServers = ConcurrentHashMap.newKeySet();
    private final Set<String> activeAlertedServers = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        if (!dnsMonitorEnabled) {
            log.info("[DNS-MONITOR] DNS monitor is disabled via configuration.");
            return;
        }

        try {
            Set<String> currentServers = queryDnsServers();
            baselineDnsServers.addAll(currentServers);
            log.info("[DNS-MONITOR] Initialized DNS baseline with {} active DNS server addresses: {}",
                    baselineDnsServers.size(), baselineDnsServers);
        } catch (Exception e) {
            log.warn("[DNS-MONITOR] Could not initialize DNS baseline: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${agent.monitors.dns-rate:${agent.monitors.rate:10000}}")
    public void check() {
        if (!dnsMonitorEnabled) {
            return;
        }

        try {
            Set<String> currentServers = queryDnsServers();
            if (currentServers.isEmpty() && !baselineDnsServers.isEmpty()) {
                // Command failed or transient network interface drop; do not clear baseline
                return;
            }

            for (String server : currentServers) {
                if (!baselineDnsServers.contains(server)) {
                    // Newly observed DNS server not present in baseline
                    if (activeAlertedServers.add(server)) {
                        log.warn("[DNS-MONITOR] [THREAT] Unauthorized DNS Server configuration detected: {}", server);
                        try {
                            dispatcher.dispatch(
                                    "DNSChanged",
                                    "Unauthorized DNS server configured on network adapter: " + server
                            );
                        } catch (Exception ex) {
                            activeAlertedServers.remove(server);
                            log.error("[DNS-MONITOR] Failed to dispatch DNSChanged event for {}", server, ex);
                        }
                    }
                }
            }

            // Cleanup alerted servers that are no longer configured
            activeAlertedServers.retainAll(currentServers);

        } catch (Exception e) {
            log.error("[DNS-MONITOR] Error during DNS configuration check", e);
        }
    }

    private Set<String> queryDnsServers() {
        CommandResult result = CommandRunner.runPowerShellWithResult(DNS_QUERY_CMD, 5);
        if (!result.isSuccess() || result.getStdout().isBlank()) {
            return Collections.emptySet();
        }

        Set<String> servers = new HashSet<>();
        String[] lines = result.getStdout().split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("0.0.0.0")) {
                servers.add(trimmed);
            }
        }
        return servers;
    }
}

