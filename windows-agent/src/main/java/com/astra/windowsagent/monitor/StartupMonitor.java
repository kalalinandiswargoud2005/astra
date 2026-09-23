package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors Windows Startup Registry keys for unauthorized persistence mechanisms.
 * 
 * Monitored Locations:
 * - HKLM\Software\Microsoft\Windows\CurrentVersion\Run
 * - HKLM\Software\Microsoft\Windows\CurrentVersion\RunOnce
 * - HKCU\Software\Microsoft\Windows\CurrentVersion\Run
 * - HKCU\Software\Microsoft\Windows\CurrentVersion\RunOnce
 * - HKLM\Software\Wow6432Node\Microsoft\Windows\CurrentVersion\Run
 * - HKLM\Software\Wow6432Node\Microsoft\Windows\CurrentVersion\RunOnce
 * 
 * Features:
 * - Baselines startup registry entries on startup.
 * - Detects newly added, modified, and removed startup registry values.
 * - Extracts entry name, registry root, executable path, and launch arguments.
 * - Analyzes suspicious startup patterns: temporary/user-writable directories, script engines,
 *   encoded commands, hidden windows, and web download cradles.
 * - Thread-safe state tracking that prevents duplicate alerts.
 * - Resilient query engine that never treats query failures or timeouts as an empty registry.
 * - Dispatches 'SuspiciousStartup' threat events without performing inline remediation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.startup.enabled:true}")
    private boolean startupMonitorEnabled = true;

    // Configurable comma-separated suspicious keywords/executables in startup command lines
    @Value("${agent.monitors.startup.suspicious-keywords:powershell,cmd.exe,wscript,cscript,mshta,rundll32,regsvr32,certutil,bitsadmin,curl.exe,-enc,-encodedcommand,hidden,bypass,downloadstring,iex,http://,https://}")
    private String suspiciousKeywordsConfig = "powershell,cmd.exe,wscript,cscript,mshta,rundll32,regsvr32,certutil,bitsadmin,curl.exe,-enc,-encodedcommand,hidden,bypass,downloadstring,iex,http://,https://";

    // Configurable comma-separated suspicious path indicators for startup commands
    @Value("${agent.monitors.startup.suspicious-paths:\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\}")
    private String suspiciousPathsConfig = "\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\";

    // Thread-safe baseline and alerted entry tracking (keyed by RegistryKey + EntryName)
    private final Map<String, StartupEntry> baselineEntries = new ConcurrentHashMap<>();
    private final Set<String> alertedEntries = ConcurrentHashMap.newKeySet();

    private static final String STARTUP_QUERY_CMD =
            "$regKeys = @(" +
            "  'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'," +
            "  'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce'," +
            "  'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'," +
            "  'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce'," +
            "  'HKLM:\\Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run'," +
            "  'HKLM:\\Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\RunOnce'" +
            "); " +
            "foreach ($key in $regKeys) { " +
            "  if (Test-Path $key) { " +
            "    $p = Get-ItemProperty -Path $key -ErrorAction SilentlyContinue; " +
            "    if ($p) { " +
            "      foreach ($prop in $p.PSObject.Properties) { " +
            "        if ($prop.Name -notin @('PSPath','PSParentPath','PSChildName','PSDrive','PSProvider')) { " +
            "          $val = if ($prop.Value) { $prop.Value.ToString().Trim() } else { '' }; " +
            "          $key + '|||' + $prop.Name + '|||' + $val; " +
            "        } " +
            "      } " +
            "    } " +
            "  } " +
            "}";

    @Getter
    public static class StartupEntry {
        private final String registryKey;
        private final String entryName;
        private final String command;
        private final String uniqueKey;

        public StartupEntry(String registryKey, String entryName, String command) {
            this.registryKey = registryKey != null ? registryKey.trim() : "";
            this.entryName = entryName != null ? entryName.trim() : "";
            this.command = command != null ? command.trim() : "";
            this.uniqueKey = (this.registryKey + "\\" + this.entryName).toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return String.format("StartupEntry[Location='%s', Name='%s', Command='%s']",
                    registryKey, entryName, command);
        }
    }

    @PostConstruct
    public void init() {
        if (!startupMonitorEnabled) {
            log.info("[STARTUP-MONITOR] Startup Registry monitor is disabled via configuration.");
            return;
        }

        Map<String, StartupEntry> initialEntries = queryStartupRegistry();
        if (initialEntries != null) {
            baselineEntries.putAll(initialEntries);
            log.info("[STARTUP-MONITOR] Initialized baseline with {} startup registry entries.", baselineEntries.size());
        } else {
            log.warn("[STARTUP-MONITOR] Could not establish initial startup registry baseline; will attempt on first check cycle.");
        }
    }

    /**
     * Periodically checks Windows startup registry keys for additions, modifications, and deletions.
     */
    @Scheduled(fixedRateString = "${agent.monitors.startup-rate:${agent.monitor.rate:60000}}")
    public void check() {
        if (!startupMonitorEnabled) {
            return;
        }

        try {
            Map<String, StartupEntry> currentEntries = queryStartupRegistry();

            // Guard: Never treat query failure or timeout as empty registry
            if (currentEntries == null) {
                log.debug("[STARTUP-MONITOR] Startup registry query returned null (failure or timeout). Skipping check cycle.");
                return;
            }

            // Establish baseline on first successful run if startup failed
            if (baselineEntries.isEmpty() && !currentEntries.isEmpty()) {
                baselineEntries.putAll(currentEntries);
                log.info("[STARTUP-MONITOR] Established initial baseline with {} startup registry entries.", baselineEntries.size());
                return;
            }

            // 1. Detect newly created or modified startup entries (NEW / MODIFIED)
            for (Map.Entry<String, StartupEntry> entry : currentEntries.entrySet()) {
                String key = entry.getKey();
                StartupEntry current = entry.getValue();

                boolean isNew = !baselineEntries.containsKey(key);
                boolean isModified = !isNew && !baselineEntries.get(key).getCommand().equalsIgnoreCase(current.getCommand());

                if (isNew || isModified) {
                    if (alertedEntries.add(key)) {
                        boolean suspicious = isSuspiciousEntry(current);

                        if (suspicious) {
                            String threatType = isModified ? "Modified" : "New";
                            String threatMessage = String.format("%s suspicious startup registry entry: %s [%s] -> %s",
                                    threatType, current.getEntryName(), current.getRegistryKey(), current.getCommand());

                            log.warn("[STARTUP-MONITOR] [THREAT] Suspicious startup entry detected: {} -> Details: {}",
                                    current.getEntryName(), current);

                            try {
                                dispatcher.dispatch("SuspiciousStartup", threatMessage);
                            } catch (Exception dispatchEx) {
                                alertedEntries.remove(key);
                                log.error("[STARTUP-MONITOR] Failed to dispatch SuspiciousStartup alert for {}", current.getEntryName(), dispatchEx);
                            }
                        } else {
                            log.info("[STARTUP-MONITOR] [NEW-ENTRY] Standard startup registry entry observed: {}", current);
                        }
                    }
                }
            }

            // 2. Detect removed startup registry entries (REMOVED / CLEANUP)
            for (String alertedKey : new ArrayList<>(alertedEntries)) {
                if (!currentEntries.containsKey(alertedKey)) {
                    log.info("[STARTUP-MONITOR] [REMEDIATION] Previously alerted startup registry entry was deleted/removed: {}", alertedKey);
                    alertedEntries.remove(alertedKey);
                }
            }

            for (String baselineKey : new ArrayList<>(baselineEntries.keySet())) {
                if (!currentEntries.containsKey(baselineKey)) {
                    log.debug("[STARTUP-MONITOR] [ENTRY-DELETED] Baseline startup entry removed: {}", baselineKey);
                    baselineEntries.remove(baselineKey);
                }
            }

        } catch (Exception e) {
            log.error("[STARTUP-MONITOR] Error during startup registry monitoring cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Queries Windows Registry for Run and RunOnce values.
     * 
     * @return Map of unique key -> StartupEntry, or null if query failed
     */
    private Map<String, StartupEntry> queryStartupRegistry() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(STARTUP_QUERY_CMD, 10);

            if (!result.isSuccess()) {
                log.debug("[STARTUP-MONITOR] Query command failed (exitCode={}, timedOut={})",
                        result.getExitCode(), result.isTimedOut());
                return null;
            }

            String output = result.getStdout().trim();
            if (output.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, StartupEntry> entries = new HashMap<>();

            for (String line : output.split("\\r?\\n")) {
                String clean = line.trim();
                if (clean.isEmpty()) {
                    continue;
                }

                if (clean.contains("|||")) {
                    String[] parts = clean.split("\\|\\|\\|", -1);
                    if (parts.length >= 3) {
                        StartupEntry entry = new StartupEntry(parts[0], parts[1], parts[2]);
                        entries.put(entry.getUniqueKey(), entry);
                    }
                }
            }

            return entries;

        } catch (Exception e) {
            log.debug("[STARTUP-MONITOR] Exception querying startup registry: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines whether a startup registry entry presents indicators of malicious persistence.
     */
    public boolean isSuspiciousEntry(StartupEntry entry) {
        if (entry == null) {
            return false;
        }

        String combined = (entry.getEntryName() + " " + entry.getCommand()).toLowerCase(Locale.ROOT);

        // 1. Check suspicious keywords / LOLBins / interpreters / download cradles
        if (suspiciousKeywordsConfig != null && !suspiciousKeywordsConfig.isBlank()) {
            for (String kw : suspiciousKeywordsConfig.split(",")) {
                String cleanKw = kw.trim().toLowerCase(Locale.ROOT);
                if (!cleanKw.isEmpty() && combined.contains(cleanKw)) {
                    return true;
                }
            }
        }

        // 2. Check suspicious user-writable or temporary paths
        if (suspiciousPathsConfig != null && !suspiciousPathsConfig.isBlank()) {
            for (String sp : suspiciousPathsConfig.split(",")) {
                String cleanPath = sp.trim().toLowerCase(Locale.ROOT);
                if (!cleanPath.isEmpty() && combined.contains(cleanPath)) {
                    return true;
                }
            }
        }

        return false;
    }
}

