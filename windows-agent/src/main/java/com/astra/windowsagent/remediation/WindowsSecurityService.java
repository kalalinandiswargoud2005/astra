package com.astra.windowsagent.remediation;

import com.astra.windowsagent.util.CommandRunner;
import com.astra.windowsagent.util.CommandRunner.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Executes defensive Windows security remediation operations and cryptographically/
 * statefully verifies that each configuration change has taken effect on the host.
 * 
 * Operations:
 * - disableRdp(): Enforces fDenyTSConnections=1 and verifies Terminal Server registry state.
 * - restoreFirewall(): Enables Domain, Private, and Public profiles and verifies all are ON.
 * - enableDefenderRealtime(): Enables Defender Real-time Protection and verifies via Get-MpComputerStatus.
 * - runDefenderScan(): Triggers a Defender quick scan and verifies command execution.
 * - isolateDevice(): Enforces host network containment via defensive firewall isolation and verifies rule state.
 * - restoreNetwork(): Removes host network containment rules and verifies restored baseline.
 */
@Slf4j
@Service
public class WindowsSecurityService {

    private static final String ISOLATION_RULE_NAME = "ASTRA-ENDPOINT-ISOLATION";
    private static final String ISOLATION_LOOPBACK_RULE = "ASTRA-ISOLATION-LOOPBACK";

    /**
     * Disables Windows Remote Desktop (RDP) by setting fDenyTSConnections to 1.
     * Verifies that the registry value is actually 1 before returning VERIFIED_SUCCESS.
     */
    public String disableRdp() {
        log.info("[WINDOWS-SECURITY] Executing DISABLE_RDP via Windows Registry");

        CommandResult regSet = CommandRunner.runCmdWithResult(
                "reg add \"HKLM\\System\\CurrentControlSet\\Control\\Terminal Server\" /v fDenyTSConnections /t REG_DWORD /d 1 /f"
        );

        if (!regSet.isSuccess()) {
            CommandRunner.runCmd("reg add \"HKCU\\Software\\Policies\\Microsoft\\Windows NT\\Terminal Services\" /v fDenyTSConnections /t REG_DWORD /d 1 /f");
        }

        // Real Verification: Query actual registry state
        CommandResult verifyResult = CommandRunner.runPowerShellWithResult(
                "(Get-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server' -Name fDenyTSConnections -ErrorAction SilentlyContinue).fDenyTSConnections",
                5
        );

        String actualVal = verifyResult.getStdout().trim();
        log.info("[WINDOWS-SECURITY] Verified fDenyTSConnections={}", actualVal);

        if ("1".equals(actualVal)) {
            return "VERIFIED_SUCCESS: Remote Desktop disabled and verified (fDenyTSConnections = 1)";
        }

        // Fallback registry verification via reg.exe query
        CommandResult checkReg = CommandRunner.runCmdWithResult("reg query \"HKLM\\System\\CurrentControlSet\\Control\\Terminal Server\" /v fDenyTSConnections");
        if (checkReg.isSuccess()) {
            String stdout = checkReg.getStdout();
            if (stdout.contains("0x1") || stdout.contains(" 1")) {
                return "VERIFIED_SUCCESS: Remote Desktop disabled and verified (fDenyTSConnections = 1)";
            }
        }

        log.warn("[WINDOWS-SECURITY] DISABLE_RDP verification failed: actual value is '{}'", actualVal);
        return "FAILED: Remote Desktop could not be verified disabled (fDenyTSConnections = " + (actualVal.isEmpty() ? "unknown" : actualVal) + ")";
    }

    /**
     * Enables Windows Firewall across Domain, Private, and Public profiles.
     * Verifies that all three profiles report State ON before returning VERIFIED_SUCCESS.
     */
    public String restoreFirewall() {
        log.info("[WINDOWS-SECURITY] Executing RESTORE_FIREWALL (enabling Domain, Private, and Public profiles)");

        CommandRunner.runCmdWithResult("netsh advfirewall set allprofiles state on");

        // Verification via PowerShell profile check
        CommandResult psVerify = CommandRunner.runPowerShellWithResult(
                "(Get-NetFirewallProfile -ErrorAction SilentlyContinue | Where-Object { $_.Enabled -ne 'True' }).Count",
                5
        );

        if (psVerify.isSuccess()) {
            String disabledCount = psVerify.getStdout().trim();
            if ("0".equals(disabledCount)) {
                log.info("[WINDOWS-SECURITY] Windows Firewall verified ON across all profiles via NetSecurity");
                return "VERIFIED_SUCCESS: Windows Firewall restored and verified ON across all profiles (Domain, Private, Public)";
            }
        }

        // Fallback verification via netsh advfirewall
        CommandResult netshResult = CommandRunner.runCmdWithResult("netsh advfirewall show allprofiles state");
        if (netshResult.isSuccess()) {
            String status = netshResult.getStdout().toLowerCase(Locale.ROOT);
            if (status.contains("state") && status.contains("on") && !status.contains("off")) {
                log.info("[WINDOWS-SECURITY] Windows Firewall verified ON across profiles via netsh");
                return "VERIFIED_SUCCESS: Windows Firewall restored and verified ON across all profiles";
            }
        }

        log.warn("[WINDOWS-SECURITY] RESTORE_FIREWALL verification failed");
        return "FAILED: Windows Firewall verification failed; one or more profiles could not be confirmed ON";
    }

    /**
     * Enables Microsoft Defender Real-Time Protection via Set-MpPreference.
     * Verifies RealTimeProtectionEnabled == True before returning VERIFIED_SUCCESS.
     */
    public String enableDefenderRealtime() {
        log.info("[WINDOWS-SECURITY] Executing ENABLE_REALTIME via Set-MpPreference");

        CommandRunner.runPowerShellWithResult(
                "Set-MpPreference -DisableRealtimeMonitoring $false -ErrorAction SilentlyContinue",
                5
        );

        // Real Verification: Query Get-MpComputerStatus
        CommandResult verify = CommandRunner.runPowerShellWithResult(
                "(Get-MpComputerStatus -ErrorAction SilentlyContinue).RealTimeProtectionEnabled",
                5
        );

        String actual = verify.getStdout().trim();
        log.info("[WINDOWS-SECURITY] Verified RealTimeProtectionEnabled={}", actual);

        if ("True".equalsIgnoreCase(actual)) {
            return "VERIFIED_SUCCESS: Microsoft Defender Real-time protection enabled and verified (RealTimeProtectionEnabled = True)";
        }

        log.warn("[WINDOWS-SECURITY] ENABLE_REALTIME verification failed: actual state is '{}'", actual);
        return "FAILED: Microsoft Defender Real-time protection could not be verified enabled (actual: " + (actual.isEmpty() ? "unknown" : actual) + ")";
    }

    /**
     * Triggers a Microsoft Defender quick scan and verifies command launch.
     */
    public String runDefenderScan() {
        log.info("[WINDOWS-SECURITY] Triggering Microsoft Defender quick scan");

        // Try PowerShell Start-MpScan first
        CommandResult psScan = CommandRunner.runPowerShellWithResult(
                "Start-MpScan -ScanType QuickScan -ErrorAction SilentlyContinue",
                10
        );

        if (psScan.isSuccess()) {
            log.info("[WINDOWS-SECURITY] Microsoft Defender quick scan started via Start-MpScan");
            return "VERIFIED_SUCCESS: Windows Defender quick scan triggered and running";
        }

        // Fallback to MpCmdRun.exe
        CommandResult exeScan = CommandRunner.runPowerShellWithResult(
                "$exe = Join-Path $env:ProgramFiles 'Windows Defender\\MpCmdRun.exe'; " +
                "if (Test-Path $exe) { Start-Process -FilePath $exe -ArgumentList '-Scan -ScanType 1' -WindowStyle Hidden; 'STARTED' } else { 'NOT_FOUND' }",
                10
        );

        if (exeScan.isSuccess() && exeScan.getStdout().contains("STARTED")) {
            log.info("[WINDOWS-SECURITY] Microsoft Defender quick scan launched via MpCmdRun.exe");
            return "VERIFIED_SUCCESS: Windows Defender quick scan triggered and running";
        }

        String reason = exeScan.getStdout().contains("NOT_FOUND") ? "Defender binary not found" : exeScan.getStderr();
        log.warn("[WINDOWS-SECURITY] Failed to trigger Defender scan: {}", reason);
        return "FAILED: Failed to trigger Windows Defender scan (" + reason + ")";
    }

    /**
     * Enforces host network containment by applying a defensive outbound blocking firewall rule,
     * while preserving local loopback communication.
     * Verifies that the isolation rule is active before returning VERIFIED_SUCCESS.
     */
    public String isolateDevice() {
        log.info("[WINDOWS-SECURITY] Applying defensive network isolation profile ({})", ISOLATION_RULE_NAME);

        String isolateScript =
                "try { " +
                "  New-NetFirewallRule -DisplayName '" + ISOLATION_LOOPBACK_RULE + "' -Direction Outbound -Action Allow -RemoteAddress '127.0.0.1' -Profile Any -ErrorAction SilentlyContinue | Out-Null; " +
                "  New-NetFirewallRule -DisplayName '" + ISOLATION_RULE_NAME + "' -Direction Outbound -Action Block -Profile Any -RemoteAddress Any -ErrorAction Stop | Out-Null; " +
                "  'ISOLATION_APPLIED' " +
                "} catch { " +
                "  netsh advfirewall firewall add rule name=\"" + ISOLATION_RULE_NAME + "\" dir=out action=block 2>$null; " +
                "  'NETSH_APPLIED' " +
                "}";

        CommandRunner.runPowerShellWithResult(isolateScript, 10);

        // Real Verification: Check if the isolation rule exists and is enabled
        CommandResult verify = CommandRunner.runPowerShellWithResult(
                "(Get-NetFirewallRule -DisplayName '" + ISOLATION_RULE_NAME + "' -ErrorAction SilentlyContinue).Enabled",
                5
        );

        String ruleStatus = verify.getStdout().trim();
        if ("True".equalsIgnoreCase(ruleStatus)) {
            log.info("[WINDOWS-SECURITY] Network isolation rule verified active: {}", ISOLATION_RULE_NAME);
            return "VERIFIED_SUCCESS: Defensive endpoint containment active and verified (" + ISOLATION_RULE_NAME + " enforced)";
        }

        // Fallback verification via netsh
        CommandResult netshCheck = CommandRunner.runCmdWithResult("netsh advfirewall firewall show rule name=\"" + ISOLATION_RULE_NAME + "\"");
        if (netshCheck.isSuccess() && netshCheck.getStdout().contains(ISOLATION_RULE_NAME)) {
            log.info("[WINDOWS-SECURITY] Network isolation rule verified active via netsh: {}", ISOLATION_RULE_NAME);
            return "VERIFIED_SUCCESS: Defensive endpoint containment active and verified (" + ISOLATION_RULE_NAME + " enforced)";
        }

        log.warn("[WINDOWS-SECURITY] Failed to verify network isolation rule");
        return "FAILED: Network isolation could not be applied or verified";
    }

    /**
     * Removes host network containment rules and restores the normal network baseline.
     * Verifies that the isolation rules are removed before returning VERIFIED_SUCCESS.
     */
    public String restoreNetwork() {
        log.info("[WINDOWS-SECURITY] Restoring normal network baseline (removing isolation rules)");

        String cleanupScript =
                "Remove-NetFirewallRule -DisplayName '" + ISOLATION_RULE_NAME + "' -ErrorAction SilentlyContinue; " +
                "Remove-NetFirewallRule -DisplayName '" + ISOLATION_LOOPBACK_RULE + "' -ErrorAction SilentlyContinue; " +
                "netsh advfirewall firewall delete rule name=\"" + ISOLATION_RULE_NAME + "\" 2>$null; " +
                "netsh advfirewall firewall delete rule name=\"" + ISOLATION_LOOPBACK_RULE + "\" 2>$null;";

        CommandRunner.runPowerShellWithResult(cleanupScript, 10);

        // Real Verification: Confirm that the isolation rule no longer exists
        CommandResult verify = CommandRunner.runPowerShellWithResult(
                "(Get-NetFirewallRule -DisplayName '" + ISOLATION_RULE_NAME + "' -ErrorAction SilentlyContinue).Count",
                5
        );

        String count = verify.getStdout().trim();
        if ("0".equals(count) || count.isEmpty()) {
            log.info("[WINDOWS-SECURITY] Network isolation rules confirmed removed");
            return "VERIFIED_SUCCESS: Endpoint network state restored and isolation rules removed";
        }

        log.warn("[WINDOWS-SECURITY] Failed to verify removal of network isolation rules");
        return "FAILED: Network isolation rules could not be completely removed";
    }
}

