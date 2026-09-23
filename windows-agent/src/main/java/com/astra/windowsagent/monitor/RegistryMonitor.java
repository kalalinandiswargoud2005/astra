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
public class RegistryMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.registry.enabled:true}")
    private boolean registryMonitorEnabled = true;

    // Thread-safe state tracking of baseline registry keys
    private final Map<String, String> baselineRunKeys = new ConcurrentHashMap<>();
    private boolean isInitialized = false;

    private static final String POWERSHELL_REGISTRY_CMD =
            "Get-ItemProperty -Path 'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run', 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -ErrorAction SilentlyContinue | Format-List -Property *";

    @Scheduled(fixedRateString = "${agent.monitors.registry-rate:${agent.monitors.rate:15000}}")
    public void check() {
        if (!registryMonitorEnabled) return;

        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(POWERSHELL_REGISTRY_CMD, 5);
            if (!result.isSuccess() || result.getStdout().isBlank()) return;

            Map<String, String> currentKeys = parseRegistryKeys(result.getStdout());
            
            if (!isInitialized) {
                baselineRunKeys.putAll(currentKeys);
                isInitialized = true;
                log.info("[REGISTRY-MONITOR] Baseline established with {} startup entries", baselineRunKeys.size());
                return;
            }

            for (Map.Entry<String, String> entry : currentKeys.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (!baselineRunKeys.containsKey(key)) {
                    log.warn("[REGISTRY-MONITOR] [THREAT] Unauthorized Registry Startup Key detected: {} -> {}", key, value);
                    dispatcher.dispatch(
                            "RegistryPersistence",
                            "Unauthorized Startup Key added: " + key + " -> " + value
                    );
                    baselineRunKeys.put(key, value);
                } else if (!baselineRunKeys.get(key).equals(value)) {
                    log.warn("[REGISTRY-MONITOR] [THREAT] Registry Startup Key modified: {} -> {}", key, value);
                    dispatcher.dispatch(
                            "RegistryPersistence",
                            "Startup Key modified: " + key + " -> " + value
                    );
                    baselineRunKeys.put(key, value);
                }
            }
        } catch (Exception e) {
            log.error("[REGISTRY-MONITOR] Error checking registry", e);
        }
    }

    private Map<String, String> parseRegistryKeys(String output) {
        Map<String, String> keys = new HashMap<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains(":") && !line.startsWith("PSPath") && !line.startsWith("PSParentPath") 
                && !line.startsWith("PSChildName") && !line.startsWith("PSDrive") && !line.startsWith("PSProvider")) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String name = line.substring(0, colonIdx).trim();
                    String val = line.substring(colonIdx + 1).trim();
                    if (!name.isEmpty() && !val.isEmpty()) {
                        keys.put(name, val);
                    }
                }
            }
        }
        return keys;
    }
}
