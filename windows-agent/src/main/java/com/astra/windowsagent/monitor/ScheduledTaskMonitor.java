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
 * Monitors Windows Scheduled Tasks for unauthorized creation, modification, and persistence.
 * 
 * Features:
 * - Baselines existing scheduled tasks on startup.
 * - Detects newly registered scheduled tasks across all task paths.
 * - Extracts task metadata: Name, Path, Author, Actions/Executables, Arguments, UserId, RunLevel, and State.
 * - Identifies suspicious indicators: temporary/user-writable directories, script interpreters,
 *   hidden execution styles, encoded payloads, download cradles, and elevated privileges.
 * - Thread-safe state tracking that prevents duplicate alerts and handles task deletion lifecycles.
 * - Resilient query engine that never treats query failures or timeouts as an empty task list.
 * - Dispatches 'SuspiciousScheduledTask' threat events without performing inline remediation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.task.enabled:true}")
    private boolean taskMonitorEnabled = true;

    // Configurable comma-separated suspicious keywords/executables in actions or arguments
    @Value("${agent.monitors.task.suspicious-keywords:powershell,cmd.exe,wscript,cscript,mshta,certutil,rundll32,regsvr32,bitsadmin,curl.exe,-enc,-encodedcommand,hidden,bypass,downloadstring,iex,invoke-expression,http://,https://}")
    private String suspiciousKeywordsConfig = "powershell,cmd.exe,wscript,cscript,mshta,certutil,rundll32,regsvr32,bitsadmin,curl.exe,-enc,-encodedcommand,hidden,bypass,downloadstring,iex,invoke-expression,http://,https://";

    // Configurable comma-separated suspicious path indicators
    @Value("${agent.monitors.task.suspicious-paths:\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\}")
    private String suspiciousPathsConfig = "\\temp\\,\\appdata\\,\\users\\public\\,c:\\programdata\\,\\downloads\\,\\desktop\\";

    // Thread-safe baselines and alerted task tracking (keyed by TaskPath + TaskName)
    private final Map<String, TaskInfo> baselineTasks = new ConcurrentHashMap<>();
    private final Set<String> alertedTasks = ConcurrentHashMap.newKeySet();

    private static final String TASK_QUERY_CMD =
            "try { " +
            "  $tasks = Get-ScheduledTask -ErrorAction Stop; " +
            "  foreach ($t in $tasks) { " +
            "    $act = if ($t.Actions) { ($t.Actions | ForEach-Object { ($_.Execute + ' ' + $_.Arguments).Trim() }) -join '; ' } else { '' }; " +
            "    $author = if ($t.Author) { $t.Author } else { 'Unknown' }; " +
            "    $user = if ($t.Principal -and $t.Principal.UserId) { $t.Principal.UserId } else { 'SYSTEM' }; " +
            "    $runLevel = if ($t.Principal -and $t.Principal.RunLevel) { $t.Principal.RunLevel } else { 'Standard' }; " +
            "    $state = if ($t.State) { $t.State } else { 'Unknown' }; " +
            "    $path = if ($t.TaskPath) { $t.TaskPath } else { '\\' }; " +
            "    $name = if ($t.TaskName) { $t.TaskName } else { '' }; " +
            "    $path + '|||' + $name + '|||' + $author + '|||' + $act + '|||' + $user + '|||' + $runLevel + '|||' + $state; " +
            "  } " +
            "} catch { " +
            "  schtasks /query /fo csv /nh 2>$null " +
            "}";

    @Getter
    public static class TaskInfo {
        private final String taskPath;
        private final String taskName;
        private final String author;
        private final String action;
        private final String userId;
        private final String runLevel;
        private final String state;
        private final String uniqueKey;

        public TaskInfo(String taskPath, String taskName, String author, String action, String userId, String runLevel, String state) {
            this.taskPath = taskPath != null ? taskPath.trim() : "\\";
            this.taskName = taskName != null ? taskName.trim() : "";
            this.author = author != null ? author.trim() : "Unknown";
            this.action = action != null ? action.trim() : "";
            this.userId = userId != null ? userId.trim() : "Unknown";
            this.runLevel = runLevel != null ? runLevel.trim() : "Standard";
            this.state = state != null ? state.trim() : "Unknown";
            this.uniqueKey = (this.taskPath.endsWith("\\") ? this.taskPath : this.taskPath + "\\") + this.taskName.toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return String.format("Task[Path='%s', Name='%s', Action='%s', RunLevel='%s', User='%s', Author='%s']",
                    taskPath, taskName, action, runLevel, userId, author);
        }
    }

    @PostConstruct
    public void init() {
        if (!taskMonitorEnabled) {
            log.info("[TASK-MONITOR] Scheduled Task monitor is disabled via configuration.");
            return;
        }

        Map<String, TaskInfo> initialTasks = queryScheduledTasks();
        if (initialTasks != null) {
            baselineTasks.putAll(initialTasks);
            log.info("[TASK-MONITOR] Initialized baseline with {} scheduled tasks.", baselineTasks.size());
        } else {
            log.warn("[TASK-MONITOR] Could not establish initial scheduled task baseline; will attempt on first check cycle.");
        }
    }

    /**
     * Periodically monitors Windows Scheduled Tasks for additions and anomalies.
     */
    @Scheduled(fixedRateString = "${agent.monitors.task-rate:${agent.monitor.rate:60000}}")
    public void check() {
        if (!taskMonitorEnabled) {
            return;
        }

        try {
            Map<String, TaskInfo> currentTasks = queryScheduledTasks();

            // Guard: Never treat query failure or timeout as empty task list
            if (currentTasks == null) {
                log.debug("[TASK-MONITOR] Task query returned null (failure or timeout). Skipping check cycle.");
                return;
            }

            // Establish baseline on first successful run if startup failed
            if (baselineTasks.isEmpty() && !currentTasks.isEmpty()) {
                baselineTasks.putAll(currentTasks);
                log.info("[TASK-MONITOR] Established initial baseline with {} scheduled tasks.", baselineTasks.size());
                return;
            }

            // 1. Detect newly added scheduled tasks (NEW)
            for (Map.Entry<String, TaskInfo> entry : currentTasks.entrySet()) {
                String key = entry.getKey();
                TaskInfo task = entry.getValue();

                if (!baselineTasks.containsKey(key)) {
                    if (alertedTasks.add(key)) {
                        boolean suspicious = isSuspiciousTask(task);

                        if (suspicious) {
                            String threatMessage = String.format("Suspicious scheduled task detected: %s (Action: %s, RunLevel: %s, User: %s)",
                                    task.getTaskName(), task.getAction(), task.getRunLevel(), task.getUserId());

                            log.warn("[TASK-MONITOR] [THREAT] Suspicious scheduled task detected: {} -> Details: {}",
                                    task.getTaskName(), task);

                            try {
                                dispatcher.dispatch("SuspiciousScheduledTask", threatMessage);
                            } catch (Exception dispatchEx) {
                                alertedTasks.remove(key);
                                log.error("[TASK-MONITOR] Failed to dispatch SuspiciousScheduledTask event for {}", task.getTaskName(), dispatchEx);
                            }
                        } else {
                            log.info("[TASK-MONITOR] [NEW-TASK] Legitimate/benign new scheduled task registered: {}", task);
                        }
                    }
                }
            }

            // 2. Detect removed scheduled tasks (REMOVED / CLEANUP)
            for (String alertedKey : new ArrayList<>(alertedTasks)) {
                if (!currentTasks.containsKey(alertedKey)) {
                    log.info("[TASK-MONITOR] [REMEDIATION] Previously alerted scheduled task was removed/deleted: {}", alertedKey);
                    alertedTasks.remove(alertedKey);
                }
            }

            for (String baselineKey : new ArrayList<>(baselineTasks.keySet())) {
                if (!currentTasks.containsKey(baselineKey)) {
                    log.debug("[TASK-MONITOR] [TASK-DELETED] Baseline scheduled task removed: {}", baselineKey);
                    baselineTasks.remove(baselineKey);
                }
            }

        } catch (Exception e) {
            log.error("[TASK-MONITOR] Error during scheduled task monitoring cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Queries Windows for scheduled tasks using PowerShell with CSV / delimited fallback.
     * 
     * @return Map of unique task key -> TaskInfo, or null if query failed
     */
    private Map<String, TaskInfo> queryScheduledTasks() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(TASK_QUERY_CMD, 15);

            if (!result.isSuccess()) {
                log.debug("[TASK-MONITOR] Query command failed (exitCode={}, timedOut={})",
                        result.getExitCode(), result.isTimedOut());
                return null;
            }

            String output = result.getStdout().trim();
            if (output.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, TaskInfo> tasks = new HashMap<>();

            for (String line : output.split("\\r?\\n")) {
                String clean = line.trim();
                if (clean.isEmpty()) {
                    continue;
                }

                // Handle '|||' delimited output from Get-ScheduledTask
                if (clean.contains("|||")) {
                    String[] parts = clean.split("\\|\\|\\|", -1);
                    if (parts.length >= 7) {
                        TaskInfo info = new TaskInfo(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]);
                        tasks.put(info.getUniqueKey(), info);
                    }
                } else if (clean.startsWith("\"") && clean.contains(",")) {
                    // Fallback: CSV output from schtasks
                    String[] parts = clean.split("\",\"");
                    if (parts.length >= 2) {
                        String rawName = parts[0].replace("\"", "").trim();
                        String state = parts.length > 2 ? parts[2].replace("\"", "").trim() : "Unknown";
                        String path = "\\";
                        String name = rawName;
                        if (rawName.contains("\\")) {
                            path = rawName.substring(0, rawName.lastIndexOf('\\') + 1);
                            name = rawName.substring(rawName.lastIndexOf('\\') + 1);
                        }
                        TaskInfo info = new TaskInfo(path, name, "schtasks", "", "Unknown", "Standard", state);
                        tasks.put(info.getUniqueKey(), info);
                    }
                }
            }

            return tasks;

        } catch (Exception e) {
            log.debug("[TASK-MONITOR] Exception querying scheduled tasks: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines whether a scheduled task presents indicators of malicious persistence or execution.
     */
    public boolean isSuspiciousTask(TaskInfo task) {
        if (task == null) {
            return false;
        }

        String combined = (task.getTaskName() + " " + task.getTaskPath() + " " + task.getAction() + " " + task.getAuthor())
                .toLowerCase(Locale.ROOT);

        // 1. Check suspicious keywords / interpreters / lolbins / download arguments
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

        // 3. Elevated tasks with non-system authors
        if ("highestavailable".equalsIgnoreCase(task.getRunLevel()) || "elevated".equalsIgnoreCase(task.getRunLevel())) {
            String authorLower = task.getAuthor().toLowerCase(Locale.ROOT);
            if (!authorLower.contains("microsoft") && !authorLower.contains("system") && !task.getAction().isBlank()) {
                return true;
            }
        }

        return false;
    }
}

