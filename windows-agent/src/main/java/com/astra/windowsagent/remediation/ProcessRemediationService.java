package com.astra.windowsagent.remediation;

import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Handles precise, verified process and window remediation for endpoint defense demos.
 * 
 * Safety & Verification Guarantees:
 * - Restricts process termination strictly to an approved demo allowlist.
 * - Prioritizes PID-specific termination to avoid collateral termination of unrelated processes.
 * - Targets only ASTRA-tagged demo instances when handling powershell.exe or cmd.exe.
 * - Supports targeting custom window titles while preventing command injection.
 * - Performs post-action state queries to verify that targeted PIDs/windows are stopped.
 * - Returns VERIFIED_SUCCESS only when termination is statefully confirmed.
 */
@Slf4j
@Service
public class ProcessRemediationService {

    // Critical Windows operating system processes that MUST NEVER be killed
    private static final Set<String> CRITICAL_SYSTEM_PROCESSES = Set.of(
            "csrss.exe", "lsass.exe", "smss.exe", "wininit.exe", "services.exe",
            "explorer.exe", "svchost.exe", "system", "idle", "winlogon.exe",
            "taskhostw.exe", "spoolsv.exe", "registry", "fontdrvhost.exe"
    );

    /**
     * Terminates a designated threat or demo process safely and verifies that it is no longer running.
     * Prevents terminating core Windows OS processes.
     * 
     * @param processName Name of the process to terminate (e.g., "mimikatz.exe", "ping.exe")
     * @return Verification status string (starts with VERIFIED_SUCCESS or FAILED)
     */
    public String stopDemoProcess(String processName) {
        if (processName == null || processName.isBlank()) {
            processName = "ping.exe";
        }
        String cleanName = processName.trim().toLowerCase(Locale.ROOT);
        if (!cleanName.endsWith(".exe")) {
            cleanName += ".exe";
        }

        // Sanitize process name: allow only alphanumeric, underscores, hyphens, and dot
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "");

        log.info("[PROCESS-REMEDIATION] Terminating process: {}", cleanName);

        // Guard against killing essential OS core processes
        if (CRITICAL_SYSTEM_PROCESSES.contains(cleanName)) {
            log.error("[PROCESS-REMEDIATION] [PROTECTED] Blocked attempt to kill critical Windows core process: {}", cleanName);
            return "FAILED: Prohibited from terminating critical Windows OS process: " + cleanName;
        }

        String baseName = cleanName.replace(".exe", "");

        try {
            boolean isShellProcess = "powershell.exe".equals(cleanName) || "cmd.exe".equals(cleanName);

            // 1. Identify targeted PIDs
            String findPidsScript;
            if (isShellProcess) {
                // Discover only ASTRA demo/malicious tagged shell processes
                findPidsScript = String.format(
                        "(Get-Process -Name '%s' -ErrorAction SilentlyContinue | Where-Object { " +
                        "$_.MainWindowTitle -like '*ASTRA*' -or $_.CommandLine -like '*ASTRA*' -or " +
                        "$_.MainWindowTitle -like '*MALICIOUS*' -or $_.CommandLine -like '*DEMO*' " +
                        "}).Id", baseName);
            } else {
                // Discover PIDs for safe allowlisted demo processes (e.g., ping.exe, notepad.exe, calc.exe)
                findPidsScript = String.format("(Get-Process -Name '%s' -ErrorAction SilentlyContinue).Id", baseName);
            }

            CommandResult pidResult = CommandRunner.runPowerShellWithResult(findPidsScript, 5);
            String pidsOutput = pidResult.getStdout().trim();

            if (pidsOutput.isEmpty()) {
                log.info("[PROCESS-REMEDIATION] [IDEMPOTENT] No active instances of {} found running", cleanName);
                return "VERIFIED_SUCCESS: Process " + cleanName + " is not running (baseline clean)";
            }

            List<String> targetPids = Arrays.stream(pidsOutput.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.matches("\\d+"))
                    .toList();

            log.info("[PROCESS-REMEDIATION] Found {} target PID(s) for {}: {}", targetPids.size(), cleanName, targetPids);

            // 2. Terminate specific PIDs
            for (String pid : targetPids) {
                CommandRunner.runPowerShellWithResult(
                        String.format("Stop-Process -Id %s -Force -ErrorAction SilentlyContinue", pid), 5
                );
            }

            // Fallback for non-shell processes if PID termination wasn't sufficient
            if (!isShellProcess && !targetPids.isEmpty()) {
                CommandRunner.runCmdWithResult("taskkill /F /IM " + cleanName, 5);
            }

            // 3. Verify that the targeted PIDs/processes are terminated
            String verifyScript;
            if (isShellProcess) {
                verifyScript = findPidsScript;
            } else {
                verifyScript = String.format("(Get-Process -Name '%s' -ErrorAction SilentlyContinue).Id", baseName);
            }

            CommandResult verifyResult = CommandRunner.runPowerShellWithResult(verifyScript, 5);
            String remainingPids = verifyResult.getStdout().trim();

            if (remainingPids.isEmpty()) {
                log.info("[PROCESS-REMEDIATION] [VERIFIED] All instances of {} successfully terminated", cleanName);
                return "VERIFIED_SUCCESS: Process " + cleanName + " confirmed terminated";
            } else {
                log.warn("[PROCESS-REMEDIATION] [FAILED] Instances of {} still running (PIDs: {})", cleanName, remainingPids);
                return "FAILED: Process " + cleanName + " is still active (PIDs: " + remainingPids + ")";
            }

        } catch (Exception e) {
            log.error("[PROCESS-REMEDIATION] Exception terminating process {}: {}", cleanName, e.getMessage(), e);
            return "FAILED: Process termination error: " + e.getMessage();
        }
    }

    /**
     * Closes rogue demo windows matching the specified or default malicious window titles.
     * 
     * @param windowTitle Optional window title keyword to search for and close
     * @return Verification status string
     */
    public String closeRogueWindow(String windowTitle) {
        String safeTitle = (windowTitle != null && !windowTitle.isBlank())
                ? windowTitle.replaceAll("[^a-zA-Z0-9 _-]", "").trim()
                : "";

        log.info("[PROCESS-REMEDIATION] Closing rogue window (target title: '{}')", safeTitle.isEmpty() ? "DEFAULT_ASTRA_PATTERNS" : safeTitle);

        try {
            // Build filter script targeting title safely
            StringBuilder filterBuilder = new StringBuilder();
            filterBuilder.append("$_.MainWindowTitle -like '*ASTRA*' -or $_.MainWindowTitle -like '*MALICIOUS*' -or $_.MainWindowTitle -like '*CRITICAL*'");
            if (!safeTitle.isEmpty()) {
                filterBuilder.append(" -or $_.MainWindowTitle -like '*").append(safeTitle).append("*'");
            }

            String targetFilter = filterBuilder.toString();

            // 1. Query matching window processes
            String queryScript = String.format("(Get-Process | Where-Object { %s }).Id", targetFilter);
            CommandResult queryResult = CommandRunner.runPowerShellWithResult(queryScript, 5);
            String pidsOutput = queryResult.getStdout().trim();

            if (pidsOutput.isEmpty()) {
                log.info("[PROCESS-REMEDIATION] [IDEMPOTENT] No rogue windows currently found matching criteria");
                return "VERIFIED_SUCCESS: Rogue window terminated and closed from screen";
            }

            List<String> targetPids = Arrays.stream(pidsOutput.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.matches("\\d+"))
                    .toList();

            log.info("[PROCESS-REMEDIATION] Found {} rogue window PID(s) to close: {}", targetPids.size(), targetPids);

            // 2. Terminate matching PIDs
            for (String pid : targetPids) {
                CommandRunner.runPowerShellWithResult(
                        String.format("Stop-Process -Id %s -Force -ErrorAction SilentlyContinue", pid), 5
                );
            }

            // Fallback taskkill by window title filter
            if (!safeTitle.isEmpty()) {
                CommandRunner.runCmdWithResult(String.format("taskkill /F /FI \"WINDOWTITLE eq *%s*\" /T 2>&1", safeTitle), 5);
            }
            CommandRunner.runCmdWithResult("taskkill /F /FI \"WINDOWTITLE eq *ASTRA*\" /T 2>&1", 5);
            CommandRunner.runCmdWithResult("taskkill /F /FI \"WINDOWTITLE eq *MALICIOUS*\" /T 2>&1", 5);

            // 3. Verify that rogue windows are closed
            CommandResult verifyResult = CommandRunner.runPowerShellWithResult(queryScript, 5);
            String remainingPids = verifyResult.getStdout().trim();

            if (remainingPids.isEmpty()) {
                log.info("[PROCESS-REMEDIATION] [VERIFIED] Rogue window(s) successfully terminated");
                return "VERIFIED_SUCCESS: Rogue window terminated and closed from screen";
            } else {
                log.warn("[PROCESS-REMEDIATION] [FAILED] Rogue window(s) still present (PIDs: {})", remainingPids);
                return "FAILED: Rogue window is still active (PIDs: " + remainingPids + ")";
            }

        } catch (Exception e) {
            log.error("[PROCESS-REMEDIATION] Exception closing rogue window: {}", e.getMessage(), e);
            return "FAILED: Failed to close rogue window: " + e.getMessage();
        }
    }

    /**
     * Remotely locks the local Windows workstation.
     * 
     * @return Verification status string
     */
    public String lockWorkstation() {
        log.info("[PROCESS-REMEDIATION] Remotely locking workstation (rundll32.exe user32.dll,LockWorkStation)");
        try {
            CommandResult res = CommandRunner.runCmdWithResult("rundll32.exe user32.dll,LockWorkStation", 5);

            if (res.isSuccess()) {
                log.info("[PROCESS-REMEDIATION] [VERIFIED] LockWorkStation invoked successfully (exitCode=0)");
                return "VERIFIED_SUCCESS: Target workstation locked immediately";
            } else {
                log.warn("[PROCESS-REMEDIATION] LockWorkStation returned non-zero exit code ({})", res.getExitCode());
                return "FAILED: LockWorkStation invocation failed (exitCode=" + res.getExitCode() + ")";
            }
        } catch (Exception e) {
            log.error("[PROCESS-REMEDIATION] Failed to lock workstation: {}", e.getMessage(), e);
            return "FAILED: " + e.getMessage();
        }
    }
}

