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

/**
 * Monitors the local Windows 'Administrators' group for membership modifications.
 * 
 * Features:
 * - Establishes a trusted baseline on startup and supports persistent/configured baselines.
 * - Detects newly added administrator accounts (escalation / persistence).
 * - Distinguishes NEW, REMOVED, and UNCHANGED administrator accounts.
 * - Normalizes account identities (DOMAIN/user, COMPUTER/user, and SIDs).
 * - Multi-stage query strategy (Get-LocalGroupMember with net localgroup fallback).
 * - Resilient against query/PowerShell failures (never treats query failure as empty group).
 * - Prevents duplicate alerts while tracking remediation when unauthorized accounts are removed.
 * - Dispatches 'NewAdministrator' threat events without performing inline remediation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdministratorMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.admin.enabled:true}")
    private boolean adminMonitorEnabled = true;

    // Optional comma-separated list of pre-approved administrator accounts / patterns
    @Value("${agent.monitors.admin.allowlist:}")
    private String adminAllowlist = "";

    // Optional comma-separated list of predefined baseline administrator accounts
    @Value("${agent.monitors.admin.baseline:}")
    private String configuredBaseline = "";

    // Thread-safe tracking of normalized baseline administrators and currently alerted accounts
    private final Set<String> baselineAdmins = ConcurrentHashMap.newKeySet();
    private final Set<String> alertedNewAdmins = ConcurrentHashMap.newKeySet();

    // Map normalized account keys to their original/display representations
    private final Map<String, String> accountDisplayNames = new ConcurrentHashMap<>();

    // Multi-stage PowerShell command: attempts Get-LocalGroupMember first, falls back to 'net localgroup'
    private static final String ADMIN_QUERY_CMD =
            "try { " +
            "  $members = Get-LocalGroupMember -Group 'Administrators' -ErrorAction Stop; " +
            "  $members | ForEach-Object { if ($_.Name) { $_.Name } elseif ($_.SID) { $_.SID.Value } else { $_.ToString() } } " +
            "} catch { " +
            "  $lines = net localgroup administrators 2>$null; " +
            "  if ($lines) { " +
            "    $capture = $false; " +
            "    foreach ($l in $lines) { " +
            "      $t = $l.Trim(); " +
            "      if ($t -match '^-+') { $capture = $true; continue }; " +
            "      if ($t -match 'The command completed') { $capture = $false; break }; " +
            "      if ($capture -and $t) { $t }; " +
            "    } " +
            "  } " +
            "}";

    @PostConstruct
    public void init() {
        if (!adminMonitorEnabled) {
            log.info("[ADMIN-MONITOR] Local Administrators group monitor is disabled via configuration.");
            return;
        }

        // 1. Seed any pre-configured baseline accounts
        if (configuredBaseline != null && !configuredBaseline.isBlank()) {
            for (String raw : configuredBaseline.split(",")) {
                String clean = raw.trim();
                if (!clean.isEmpty()) {
                    String norm = normalizeAccount(clean);
                    baselineAdmins.add(norm);
                    accountDisplayNames.put(norm, clean);
                }
            }
        }

        // 2. Discover existing administrators dynamically at startup
        Map<String, String> initialMembers = queryAdminGroupMembers();
        if (initialMembers != null) {
            baselineAdmins.addAll(initialMembers.keySet());
            accountDisplayNames.putAll(initialMembers);
            log.info("[ADMIN-MONITOR] Initialized local Administrators baseline with {} accounts: {}",
                    baselineAdmins.size(), accountDisplayNames.values());
        } else {
            log.warn("[ADMIN-MONITOR] Could not query initial local Administrators group members; baseline will be established on first successful cycle.");
        }
    }

    /**
     * Periodically monitors the local Administrators group for membership modifications.
     */
    @Scheduled(fixedRateString = "${agent.monitors.admin-rate:${agent.monitors.rate:1500}}")
    public void check() {
        if (!adminMonitorEnabled) {
            return;
        }

        try {
            Map<String, String> currentMembers = queryAdminGroupMembers();

            // Guard: Never treat query failure or timeout as an empty group
            if (currentMembers == null) {
                log.debug("[ADMIN-MONITOR] Query returned null (query failure/timeout). Skipping check cycle.");
                return;
            }

            // Establish baseline on first successful run if startup query failed
            if (baselineAdmins.isEmpty() && !currentMembers.isEmpty()) {
                baselineAdmins.addAll(currentMembers.keySet());
                accountDisplayNames.putAll(currentMembers);
                log.info("[ADMIN-MONITOR] Established initial local Administrators baseline: {}", accountDisplayNames.values());
                return;
            }

            accountDisplayNames.putAll(currentMembers);

            // 1. Detect newly added administrator accounts (NEW)
            for (Map.Entry<String, String> entry : currentMembers.entrySet()) {
                String normAccount = entry.getKey();
                String displayAccount = entry.getValue();

                if (!baselineAdmins.contains(normAccount)) {
                    if (alertedNewAdmins.add(normAccount)) {
                        boolean isApproved = isAccountApproved(normAccount, displayAccount);
                        String threatMessage = isApproved
                                ? "Authorized new administrator added to local Administrators group: " + displayAccount
                                : "New administrator account detected in local Administrators group: " + displayAccount;

                        log.warn("[ADMIN-MONITOR] [THREAT] Member added to local Administrators group: {} (Normalized: {}, Authorized: {})",
                                displayAccount, normAccount, isApproved);

                        try {
                            dispatcher.dispatch("NewAdministrator", threatMessage);
                        } catch (Exception dispatchEx) {
                            alertedNewAdmins.remove(normAccount);
                            log.error("[ADMIN-MONITOR] Failed to dispatch NewAdministrator threat event for {}", displayAccount, dispatchEx);
                        }
                    }
                }
            }

            // 2. Detect removed administrator accounts (REMOVED)
            // (a) Check previously alerted new accounts that have now been removed (e.g., remediation)
            for (String alertedNorm : new ArrayList<>(alertedNewAdmins)) {
                if (!currentMembers.containsKey(alertedNorm)) {
                    String displayName = accountDisplayNames.getOrDefault(alertedNorm, alertedNorm);
                    log.info("[ADMIN-MONITOR] [REMEDIATION] Flagged administrator account removed from local Administrators group: {}", displayName);
                    alertedNewAdmins.remove(alertedNorm);
                }
            }

            // (b) Track when baseline members are removed
            for (String baselineNorm : new ArrayList<>(baselineAdmins)) {
                if (!currentMembers.containsKey(baselineNorm)) {
                    String displayName = accountDisplayNames.getOrDefault(baselineNorm, baselineNorm);
                    log.info("[ADMIN-MONITOR] [MEMBERSHIP-CHANGE] Baseline administrator account removed: {}", displayName);
                    baselineAdmins.remove(baselineNorm);
                }
            }

        } catch (Exception e) {
            log.error("[ADMIN-MONITOR] Error during Administrators group check cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Queries Windows for members of the local Administrators group.
     * 
     * @return Map of normalized account identifier -> display name, or null if query failed
     */
    private Map<String, String> queryAdminGroupMembers() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(ADMIN_QUERY_CMD, 5);

            if (!result.isSuccess()) {
                log.debug("[ADMIN-MONITOR] Query command exited with non-zero code ({})", result.getExitCode());
                return null;
            }

            String output = result.getStdout().trim();
            Map<String, String> members = new HashMap<>();

            if (!output.isEmpty()) {
                for (String line : output.split("\\r?\\n")) {
                    String clean = line.trim();
                    if (!clean.isEmpty()) {
                        String norm = normalizeAccount(clean);
                        members.put(norm, clean);
                    }
                }
            }

            return members;

        } catch (Exception e) {
            log.debug("[ADMIN-MONITOR] Exception executing group query: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalizes account identifiers (DOMAIN\User, COMPUTER\User, User, or SID) into a canonical key.
     */
    private String normalizeAccount(String rawAccount) {
        if (rawAccount == null || rawAccount.isBlank()) {
            return "";
        }
        String normalized = rawAccount.trim().replace('/', '\\').toLowerCase(Locale.ROOT);
        return normalized;
    }

    /**
     * Checks if the account matches any configured trusted administrator pattern or allowlist entry.
     */
    private boolean isAccountApproved(String normAccount, String displayAccount) {
        if (adminAllowlist == null || adminAllowlist.isBlank()) {
            return false;
        }

        String normDisplay = displayAccount.toLowerCase(Locale.ROOT).trim();
        String shortName = normAccount.contains("\\")
                ? normAccount.substring(normAccount.lastIndexOf('\\') + 1)
                : normAccount;

        return Arrays.stream(adminAllowlist.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> {
                    String cleanPattern = pattern.replace('/', '\\');
                    return normAccount.equals(cleanPattern)
                            || normDisplay.equals(cleanPattern)
                            || shortName.equals(cleanPattern)
                            || normAccount.endsWith("\\" + cleanPattern)
                            || normAccount.contains(cleanPattern);
                });
    }
}

