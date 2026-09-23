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

@Slf4j
@Component
@RequiredArgsConstructor
public class VPNMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.vpn.enabled:true}")
    private boolean vpnMonitorEnabled = true;

    // Thread-safe tracking of currently active (Up) VPN connections (AdapterName -> Description)
    private final Map<String, String> activeVpnAdapters = new ConcurrentHashMap<>();

    private static final String ADAPTER_QUERY_CMD =
            "Get-NetAdapter | Format-Table -HideTableHeaders Name, Status, InterfaceDescription";

    private static final List<String> VPN_KEYWORDS = List.of(
            "vpn", "tun", "tap", "wireguard", "openvpn", "tailscale",
            "zerotier", "fortinet", "palo alto", "anyconnect", "nordvpn",
            "expressvpn", "checkpoint", "pulse secure", "surfshark",
            "protonvpn", "mullvad", "windscribe", "cisco", "sonicwall",
            "globalprotect", "wan miniport (ikev2)", "wan miniport (l2tp)",
            "wan miniport (pptp)", "wan miniport (sstp)"
    );

    private static final List<String> EXCLUDED_ADAPTER_KEYWORDS = List.of(
            "virtualbox", "vmware", "hyper-v", "loopback", "bluetooth"
    );

    @PostConstruct
    public void init() {
        if (!vpnMonitorEnabled) {
            log.info("[VPN-MONITOR] VPN monitor is disabled via configuration.");
            return;
        }

        Map<String, String> initialActive = queryActiveVpnAdapters();
        if (initialActive != null) {
            activeVpnAdapters.putAll(initialActive);
            log.info("[VPN-MONITOR] Initialized baseline active VPN adapters: {}",
                    activeVpnAdapters.isEmpty() ? "[None]" : activeVpnAdapters.keySet());
        } else {
            log.warn("[VPN-MONITOR] Could not establish initial VPN baseline (query failed).");
        }
    }

    /**
     * Periodically monitors Windows network interfaces for VPN connection and disconnection events.
     */
    @Scheduled(fixedRateString = "${agent.monitors.vpn-rate:${agent.monitors.rate:2000}}")
    public void check() {
        if (!vpnMonitorEnabled) {
            return;
        }

        try {
            Map<String, String> currentActiveVpns = queryActiveVpnAdapters();

            // Never process or treat query failure / timeout as a disconnect
            if (currentActiveVpns == null) {
                log.debug("[VPN-MONITOR] Skipping check cycle; network adapter query failed.");
                return;
            }

            // 1. Detect newly connected / activated VPN interfaces
            for (Map.Entry<String, String> entry : currentActiveVpns.entrySet()) {
                String adapterName = entry.getKey();
                String description = entry.getValue();

                if (!activeVpnAdapters.containsKey(adapterName)) {
                    log.info("[VPN-MONITOR] [CONNECTED] Active VPN tunnel established on: {} ({})", adapterName, description);

                    try {
                        dispatcher.dispatch(
                                "VPNConnected",
                                "VPN tunnel active on adapter: " + adapterName + " (" + description + ")"
                        );
                        activeVpnAdapters.put(adapterName, description);
                    } catch (Exception ex) {
                        log.error("[VPN-MONITOR] Failed to dispatch VPNConnected event for {}", adapterName, ex);
                    }
                }
            }

            // 2. Detect disconnected / deactivated VPN interfaces
            for (Map.Entry<String, String> entry : new ArrayList<>(activeVpnAdapters.entrySet())) {
                String adapterName = entry.getKey();
                String description = entry.getValue();

                if (!currentActiveVpns.containsKey(adapterName)) {
                    log.warn("[VPN-MONITOR] [THREAT] Active VPN tunnel DISCONNECTED: {} ({})", adapterName, description);

                    try {
                        dispatcher.dispatch(
                                "VPNDisconnected",
                                "Corporate / Encrypted VPN interface disconnected: " + adapterName + " (" + description + ")"
                        );
                        activeVpnAdapters.remove(adapterName);
                    } catch (Exception ex) {
                        log.error("[VPN-MONITOR] Failed to dispatch VPNDisconnected event for {}", adapterName, ex);
                    }
                }
            }

        } catch (Exception e) {
            log.error("[VPN-MONITOR] Error during VPN status check", e);
        }
    }

    /**
     * Queries Windows network adapters and returns a map of currently active (Up) VPN adapters.
     * Returns:
     * - Map of adapterName -> description for active VPNs
     * - Empty map if no VPNs are currently active (Up)
     * - null if the command failed, timed out, or threw an error
     */
    private Map<String, String> queryActiveVpnAdapters() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(ADAPTER_QUERY_CMD, 5);

            if (!result.isSuccess()) {
                log.debug("[VPN-MONITOR] Adapter query failed (exitCode={})", result.getExitCode());
                return null;
            }

            String output = result.getStdout().trim();
            Map<String, String> upVpns = new HashMap<>();

            if (output.isEmpty()) {
                return upVpns;
            }

            for (String line : output.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Split into tokens: Name (can have spaces), Status, Description
                String[] tokens = trimmed.split("\\s{2,}");
                if (tokens.length >= 2) {
                    String adapterName = tokens[0].trim();
                    String status = tokens[1].trim();
                    String description = tokens.length >= 3 ? tokens[2].trim() : adapterName;

                    if (isVpnInterface(adapterName, description)) {
                        boolean isUp = "Up".equalsIgnoreCase(status) || "Connected".equalsIgnoreCase(status);
                        if (isUp) {
                            upVpns.put(adapterName, description);
                        }
                    }
                }
            }

            return upVpns;

        } catch (Exception e) {
            log.debug("[VPN-MONITOR] Failed to query network adapters: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Inspects adapter name and interface description to identify genuine VPN interfaces.
     */
    private boolean isVpnInterface(String name, String description) {
        String combined = (name + " " + description).toLowerCase(Locale.ROOT);

        // Check if excluded (pure virtual machine or local peripheral adapters)
        for (String excluded : EXCLUDED_ADAPTER_KEYWORDS) {
            if (combined.contains(excluded) && !combined.contains("tap") && !combined.contains("vpn")) {
                return false;
            }
        }

        // Match against known VPN indicators
        for (String keyword : VPN_KEYWORDS) {
            if (combined.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}
