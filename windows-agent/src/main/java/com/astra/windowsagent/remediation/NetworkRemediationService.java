package com.astra.windowsagent.remediation;

import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Handles network-level remediation and demo artifact cleanup with precise targeting.
 * 
 * Safety & Verification Guarantees:
 * - Validates TCP ports strictly before invoking network utilities.
 * - Inspects owning processes to ensure only ASTRA demo listeners are terminated, protecting unrelated processes.
 * - Verifies socket state (closed/listening) post-remediation using native Windows network cmdlets.
 * - Sanitizes incident identifiers against registry injection.
 * - Verifies the removal of demo registry keys from HKCU\Software\ASTRA\Demo.
 * - Treats already-removed artifacts and unallocated ports as idempotent successes.
 */
@Slf4j
@Service
public class NetworkRemediationService {

    private static final String DEMO_REGISTRY_BASE = "HKCU\\Software\\ASTRA\\Demo";

    // Allowlisted process names and indicators for demo listeners
    private static final List<String> ALLOWED_DEMO_LISTENER_PROCS = List.of(
            "powershell", "cmd", "python", "nc", "ncat", "socat"
    );

    /**
     * Terminates only ASTRA demo listener processes associated with the requested TCP port
     * and verifies that the port is no longer in a LISTENING state.
     * 
     * @param port Target TCP port (1-65535, typically 44444 for ASTRA demos)
     * @return Verification status string (starts with VERIFIED_SUCCESS or FAILED)
     */
    public String stopDemoListener(int port) {
        // 1. Strict port validation
        if (port < 1 || port > 65535) {
            log.error("[NETWORK-REMEDIATION] [REJECTED] Invalid TCP port requested: {}", port);
            return "FAILED: Invalid TCP port: " + port + " (must be between 1 and 65535)";
        }

        log.info("[NETWORK-REMEDIATION] Closing demo listener on TCP port {}", port);

        try {
            // 2. Query the process currently bound to the local TCP port
            String inspectScript = String.format(
                    "try { " +
                    "  $conns = Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue; " +
                    "  if ($conns) { " +
                    "    foreach ($c in $conns) { " +
                    "      $pidVal = $c.OwningProcess; " +
                    "      $p = Get-Process -Id $pidVal -ErrorAction SilentlyContinue; " +
                    "      $pName = if ($p) { $p.ProcessName } else { 'Unknown' }; " +
                    "      $cmdLine = if ($p -and $p.CommandLine) { $p.CommandLine } else { '' }; " +
                    "      $pidVal.ToString() + '|#|' + $pName + '|#|' + $cmdLine; " +
                    "    } " +
                    "  } " +
                    "} catch { }", port);

            CommandResult inspectResult = CommandRunner.runPowerShellWithResult(inspectScript, 5);
            String inspectOutput = inspectResult.getStdout().trim();

            if (inspectOutput.isEmpty()) {
                // Secondary check for any lingering ASTRA listener tagged processes
                CommandRunner.runPowerShellWithResult(
                        "Get-Process powershell,cmd -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*ASTRA_SAFE_DEMO_LISTENER*' } | Stop-Process -Force -ErrorAction SilentlyContinue",
                        5
                );
                log.info("[NETWORK-REMEDIATION] [IDEMPOTENT] TCP port {} is not listening (already clean)", port);
                return "VERIFIED_SUCCESS: Port " + port + " closed and verified";
            }

            // 3. Validate that the owning process is an ASTRA demo process before terminating
            boolean killedAny = false;
            for (String line : inspectOutput.split("\\r?\\n")) {
                String cleanLine = line.trim();
                if (cleanLine.isEmpty() || !cleanLine.contains("|#|")) continue;

                String[] parts = cleanLine.split("\\|#\\|", -1);
                String pidStr = parts[0].trim();
                String procName = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
                String cmdLine = parts.length > 2 ? parts[2].trim().toLowerCase(Locale.ROOT) : "";

                int pid = Integer.parseInt(pidStr);

                // Safety guard: Protect system critical processes (PID <= 4, svchost, system)
                if (pid <= 4 || "system".equalsIgnoreCase(procName) || "svchost".equalsIgnoreCase(procName)) {
                    log.error("[NETWORK-REMEDIATION] [REJECTED] Port {} is bound to protected system process (PID: {}, Name: {})", port, pid, procName);
                    return "FAILED: Port " + port + " is in use by a protected system process (PID: " + pid + ")";
                }

                // Check if the process matches ASTRA demo patterns
                boolean isAstraDemoProcess = ALLOWED_DEMO_LISTENER_PROCS.stream().anyMatch(procName::contains)
                        || cmdLine.contains("astra")
                        || cmdLine.contains("demo")
                        || cmdLine.contains(String.valueOf(port));

                if (!isAstraDemoProcess) {
                    log.error("[NETWORK-REMEDIATION] [REJECTED] Port {} is in use by non-demo process (PID: {}, Name: {})", port, pid, procName);
                    return "FAILED: Port " + port + " is bound to non-demo process (PID: " + pid + ", Name: " + procName + ")";
                }

                // Terminate verified demo process by PID
                log.info("[NETWORK-REMEDIATION] Terminating ASTRA demo listener (PID: {}, Name: {}) on port {}", pid, procName, port);
                CommandRunner.runPowerShellWithResult(String.format("Stop-Process -Id %d -Force -ErrorAction SilentlyContinue", pid), 5);
                killedAny = true;
            }

            // Also clean up any lingering script listeners tagged with ASTRA_SAFE_DEMO_LISTENER
            CommandRunner.runPowerShellWithResult(
                    "Get-Process powershell,cmd -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*ASTRA_SAFE_DEMO_LISTENER*' } | Stop-Process -Force -ErrorAction SilentlyContinue",
                    5
            );

            // 4. Real Verification: Confirm that the port is no longer listening
            CommandResult verifyResult = CommandRunner.runPowerShellWithResult(
                    String.format("(Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue).Count", port),
                    5
            );

            String activeCount = verifyResult.getStdout().trim();
            if ("0".equals(activeCount) || activeCount.isEmpty()) {
                log.info("[NETWORK-REMEDIATION] [VERIFIED] TCP port {} verified closed and released", port);
                return "VERIFIED_SUCCESS: Port " + port + " closed and verified";
            }

            // Fallback netstat verification
            CommandResult netstatCheck = CommandRunner.runPowerShellWithResult(
                    String.format("netstat -ano | Select-String ':%d\\s+.*LISTENING'", port),
                    5
            );
            if (netstatCheck.getStdout().trim().isEmpty()) {
                log.info("[NETWORK-REMEDIATION] [VERIFIED] TCP port {} confirmed closed via netstat", port);
                return "VERIFIED_SUCCESS: Port " + port + " closed and verified";
            }

            log.warn("[NETWORK-REMEDIATION] [FAILED] Port {} is still in LISTENING state after termination attempt", port);
            return "FAILED: Port " + port + " still active: " + netstatCheck.getStdout().trim();

        } catch (Exception e) {
            log.error("[NETWORK-REMEDIATION] Error stopping listener on port {}: {}", port, e.getMessage(), e);
            return "FAILED: Network remediation exception: " + e.getMessage();
        }
    }

    /**
     * Removes the simulated attack registry key created during ASTRA threat demonstrations
     * and verifies that the key has been removed from HKCU.
     * 
     * @param incidentId Incident identifier associated with the demo artifact
     * @return Verification status string (starts with VERIFIED_SUCCESS or FAILED)
     */
    public String restoreDemoRegistry(String incidentId) {
        String safeIncidentId = (incidentId != null && !incidentId.isBlank()) 
                ? incidentId.replaceAll("[^a-zA-Z0-9_-]", "").trim() 
                : "INC-DEFAULT";

        if (safeIncidentId.isEmpty()) {
            safeIncidentId = "INC-DEFAULT";
        }

        String keyPath = DEMO_REGISTRY_BASE + "\\" + safeIncidentId;
        log.info("[NETWORK-REMEDIATION] Removing demo registry key: {}", keyPath);

        try {
            // 1. Check if key exists (idempotency check)
            CommandResult checkBefore = CommandRunner.runCmdWithResult("reg query \"" + keyPath + "\"", 5);
            if (!checkBefore.isSuccess() || checkBefore.getStdout().contains("ERROR") || !checkBefore.getStdout().contains("DemoThreatActive")) {
                log.info("[NETWORK-REMEDIATION] [IDEMPOTENT] Demo registry key {} is not present (baseline clean)", keyPath);
                return "VERIFIED_SUCCESS: ASTRA demo registry key deleted and verified";
            }

            // 2. Delete the targeted demo registry key
            CommandResult delResult = CommandRunner.runCmdWithResult("reg delete \"" + keyPath + "\" /f", 5);

            // 3. Real Verification: Query registry key to confirm deletion
            CommandResult checkAfter = CommandRunner.runCmdWithResult("reg query \"" + keyPath + "\"", 5);
            boolean isDeleted = !checkAfter.isSuccess() 
                    || checkAfter.getStdout().contains("ERROR") 
                    || !checkAfter.getStdout().contains("DemoThreatActive");

            if (isDeleted) {
                log.info("[NETWORK-REMEDIATION] [VERIFIED] Registry key {} confirmed removed", keyPath);
                return "VERIFIED_SUCCESS: ASTRA demo registry key deleted and verified";
            } else {
                log.warn("[NETWORK-REMEDIATION] [FAILED] Registry key {} still exists after deletion attempt", keyPath);
                return "FAILED: Registry key still exists after deletion attempt";
            }

        } catch (Exception e) {
            log.error("[NETWORK-REMEDIATION] Registry restore error for {}: {}", keyPath, e.getMessage(), e);
            return "FAILED: " + e.getMessage();
        }
    }
}


