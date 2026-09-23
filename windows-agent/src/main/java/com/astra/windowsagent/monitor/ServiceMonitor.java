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
 * Monitors Windows Services for unauthorized creation, suspicious configurations,
 * and critical security service stoppages.
 * 
 * Features:
 * - Baselines installed Windows services on startup.
 * - Detects newly installed/registered Windows services (persistence / privilege escalation).
 * - Detects unexpected stoppage of critical security services (Defender, Firewall, EventLog).
 * - Extracts service metadata: Name, DisplayName, State, StartMode, PathName (BinaryPath),
 *   StartName (ServiceAccount), and Description.
 * - Analyzes suspicious service indicators: user-writable / temporary directories, script engines,
 *   encoded execution arguments, and abnormal service binaries.
 * - Thread-safe state tracking that prevents duplicate alerts and handles uninstallation lifecycles.
 * - Resilient query engine that never treats query failures or timeouts as an empty service list.
 * - Dispatches 'CriticalServiceStopped' and 'SuspiciousStartup' threat events without performing inline remediation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.service.enabled:true}")
    private boolean serviceMonitorEnabled = true;

    // Configurable comma-separated suspicious keywords/binaries in service paths
    @Value("${agent.monitors.service.suspicious-keywords:powershell,cmd.exe,wscript,cscript,mshta,rundll32,regsvr32,certutil,bitsadmin,curl.exe,-enc,-encodedcommand,bypass,downloadstring,iex}")
    private String suspiciousKeywordsConfig = "powershell,cmd.exe,wscript,cscript,mshta,rundll32,regsvr32,certutil,bitsadmin,curl.exe,-enc,-encodedcommand,bypass,downloadstring,iex";

    // Configurable comma-separated suspicious path locations for service executables
    @Value("${agent.monitors.service.suspicious-paths:\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\}")
    private String suspiciousPathsConfig = "\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\";

    // Critical security service identifiers to monitor for unexpected stoppage
    @Value("${agent.monitors.service.critical:windefend,mpssvc,sense,eventlog,wscsvc,bfe}")
    private String criticalServicesConfig = "windefend,mpssvc,sense,eventlog,wscsvc,bfe";

    // Thread-safe baselines and alerted state tracking
    private final Map<String, ServiceInfo> baselineServices = new ConcurrentHashMap<>();
    private final Set<String> alertedNewServices = ConcurrentHashMap.newKeySet();
    private final Set<String> alertedStoppedCriticalServices = ConcurrentHashMap.newKeySet();

    private static final String SERVICE_QUERY_CMD =
            "try { " +
            "  $services = Get-CimInstance Win32_Service -ErrorAction Stop; " +
            "  foreach ($s in $services) { " +
            "    $desc = if ($s.Description) { $s.Description.Replace(\"`n\", ' ').Replace(\"`r\", ' ') } else { '' }; " +
            "    $path = if ($s.PathName) { $s.PathName.Trim() } else { '' }; " +
            "    $user = if ($s.StartName) { $s.StartName.Trim() } else { 'LocalSystem' }; " +
            "    $s.Name + '|||' + $s.DisplayName + '|||' + $s.State + '|||' + $s.StartMode + '|||' + $path + '|||' + $user + '|||' + $desc; " +
            "  } " +
            "} catch { " +
            "  Get-Service | ForEach-Object { $_.Name + '|||' + $_.DisplayName + '|||' + $_.Status + '|||Automatic||||||' } " +
            "}";

    @Getter
    public static class ServiceInfo {
        private final String name;
        private final String displayName;
        private final String state;
        private final String startMode;
        private final String binaryPath;
        private final String serviceAccount;
        private final String description;
        private final String uniqueKey;

        public ServiceInfo(String name, String displayName, String state, String startMode, String binaryPath, String serviceAccount, String description) {
            this.name = name != null ? name.trim() : "";
            this.displayName = displayName != null ? displayName.trim() : "";
            this.state = state != null ? state.trim() : "Unknown";
            this.startMode = startMode != null ? startMode.trim() : "Unknown";
            this.binaryPath = binaryPath != null ? binaryPath.trim() : "";
            this.serviceAccount = serviceAccount != null ? serviceAccount.trim() : "LocalSystem";
            this.description = description != null ? description.trim() : "";
            this.uniqueKey = this.name.toLowerCase(Locale.ROOT);
        }

        public boolean isRunning() {
            return "running".equalsIgnoreCase(state);
        }

        public boolean isStopped() {
            return "stopped".equalsIgnoreCase(state);
        }

        @Override
        public String toString() {
            return String.format("Service[Name='%s', Display='%s', State='%s', StartMode='%s', Path='%s', Account='%s']",
                    name, displayName, state, startMode, binaryPath, serviceAccount);
        }
    }

    @PostConstruct
    public void init() {
        if (!serviceMonitorEnabled) {
            log.info("[SERVICE-MONITOR] Windows Service monitor is disabled via configuration.");
            return;
        }

        Map<String, ServiceInfo> initialServices = queryWindowsServices();
        if (initialServices != null) {
            baselineServices.putAll(initialServices);
            log.info("[SERVICE-MONITOR] Initialized baseline with {} Windows services.", baselineServices.size());
        } else {
            log.warn("[SERVICE-MONITOR] Could not establish initial Windows service baseline; will attempt on first check cycle.");
        }
    }

    /**
     * Periodically monitors Windows Services for additions, state changes, and anomalies.
     */
    @Scheduled(fixedRateString = "${agent.monitors.service-rate:${agent.monitor.rate:60000}}")
    public void check() {
        if (!serviceMonitorEnabled) {
            return;
        }

        try {
            Map<String, ServiceInfo> currentServices = queryWindowsServices();

            // Guard: Never treat query failure or timeout as empty service list
            if (currentServices == null) {
                log.debug("[SERVICE-MONITOR] Service query returned null (failure or timeout). Skipping check cycle.");
                return;
            }

            // Establish baseline on first successful run if startup failed
            if (baselineServices.isEmpty() && !currentServices.isEmpty()) {
                baselineServices.putAll(currentServices);
                log.info("[SERVICE-MONITOR] Established initial baseline with {} Windows services.", baselineServices.size());
                return;
            }

            // 1. Detect newly created Windows services (NEW)
            for (Map.Entry<String, ServiceInfo> entry : currentServices.entrySet()) {
                String key = entry.getKey();
                ServiceInfo service = entry.getValue();

                if (!baselineServices.containsKey(key)) {
                    if (alertedNewServices.add(key)) {
                        boolean suspicious = isSuspiciousService(service);

                        if (suspicious) {
                            String threatMessage = String.format("Suspicious Windows service created: %s (%s) -> Path: %s, Account: %s",
                                    service.getName(), service.getDisplayName(), service.getBinaryPath(), service.getServiceAccount());

                            log.warn("[SERVICE-MONITOR] [THREAT] Suspicious Windows service detected: {} -> Details: {}",
                                    service.getName(), service);

                            try {
                                dispatcher.dispatch("SuspiciousStartup", threatMessage);
                            } catch (Exception dispatchEx) {
                                alertedNewServices.remove(key);
                                log.error("[SERVICE-MONITOR] Failed to dispatch threat event for new service {}", service.getName(), dispatchEx);
                            }
                        } else {
                            log.info("[SERVICE-MONITOR] [NEW-SERVICE] Standard Windows service installed: {}", service);
                        }
                    }
                }
            }

            // 2. Detect stopped critical security services
            Set<String> criticalSet = getCriticalServicesSet();
            for (String criticalName : criticalSet) {
                ServiceInfo current = currentServices.get(criticalName);
                if (current != null) {
                    if (current.isStopped()) {
                        if (alertedStoppedCriticalServices.add(criticalName)) {
                            String alertMsg = String.format("Critical security service stopped: %s (%s)",
                                    current.getName(), current.getDisplayName());
                            log.warn("[SERVICE-MONITOR] [CRITICAL-STOPPED] {}", alertMsg);
                            try {
                                dispatcher.dispatch("CriticalServiceStopped", alertMsg);
                            } catch (Exception dispatchEx) {
                                alertedStoppedCriticalServices.remove(criticalName);
                                log.error("[SERVICE-MONITOR] Failed to dispatch CriticalServiceStopped event for {}", criticalName, dispatchEx);
                            }
                        }
                    } else if (current.isRunning()) {
                        if (alertedStoppedCriticalServices.remove(criticalName)) {
                            log.info("[SERVICE-MONITOR] [RECOVERY] Critical security service is running again: {}", current.getName());
                        }
                    }
                }
            }

            // 3. Detect uninstalled / removed services (REMOVED)
            for (String alertedKey : new ArrayList<>(alertedNewServices)) {
                if (!currentServices.containsKey(alertedKey)) {
                    log.info("[SERVICE-MONITOR] [REMOVED] Previously alerted service was uninstalled/removed: {}", alertedKey);
                    alertedNewServices.remove(alertedKey);
                }
            }

            for (String baselineKey : new ArrayList<>(baselineServices.keySet())) {
                if (!currentServices.containsKey(baselineKey)) {
                    log.debug("[SERVICE-MONITOR] [SERVICE-DELETED] Baseline service removed: {}", baselineKey);
                    baselineServices.remove(baselineKey);
                }
            }

        } catch (Exception e) {
            log.error("[SERVICE-MONITOR] Error during Windows service monitoring cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Queries Windows for installed services using CIM/WMI with Get-Service fallback.
     * 
     * @return Map of unique service key -> ServiceInfo, or null if query failed
     */
    private Map<String, ServiceInfo> queryWindowsServices() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(SERVICE_QUERY_CMD, 15);

            if (!result.isSuccess()) {
                log.debug("[SERVICE-MONITOR] Query command failed (exitCode={}, timedOut={})",
                        result.getExitCode(), result.isTimedOut());
                return null;
            }

            String output = result.getStdout().trim();
            if (output.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, ServiceInfo> services = new HashMap<>();

            for (String line : output.split("\\r?\\n")) {
                String clean = line.trim();
                if (clean.isEmpty()) {
                    continue;
                }

                if (clean.contains("|||")) {
                    String[] parts = clean.split("\\|\\|\\|", -1);
                    if (parts.length >= 7) {
                        ServiceInfo info = new ServiceInfo(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]);
                        services.put(info.getUniqueKey(), info);
                    }
                }
            }

            return services;

        } catch (Exception e) {
            log.debug("[SERVICE-MONITOR] Exception querying Windows services: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines whether a Windows service presents indicators of suspicious activity or stealth persistence.
     */
    public boolean isSuspiciousService(ServiceInfo service) {
        if (service == null) {
            return false;
        }

        String combined = (service.getName() + " " + service.getDisplayName() + " " + service.getBinaryPath() + " " + service.getDescription())
                .toLowerCase(Locale.ROOT);

        // 1. Check suspicious keywords / LOLBins in binary path
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

    private Set<String> getCriticalServicesSet() {
        Set<String> set = new HashSet<>();
        if (criticalServicesConfig != null && !criticalServicesConfig.isBlank()) {
            for (String s : criticalServicesConfig.split(",")) {
                String clean = s.trim().toLowerCase(Locale.ROOT);
                if (!clean.isEmpty()) {
                    set.add(clean);
                }
            }
        }
        return set;
    }
}

