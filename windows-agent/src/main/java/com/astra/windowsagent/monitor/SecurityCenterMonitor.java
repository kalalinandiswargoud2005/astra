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

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityCenterMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.security-center.enabled:true}")
    private boolean securityCenterEnabled = true;

    private static final String SECURITY_CENTER_QUERY_CMD =
            "try { Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct -ErrorAction SilentlyContinue | " +
            "ForEach-Object { $_.displayName + '|' + $_.productState } } catch { }";

    private final AtomicBoolean wasAlertDispatched = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (!securityCenterEnabled) {
            log.info("[SECURITY-CENTER] Security Center monitor is disabled via configuration.");
            return;
        }

        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(SECURITY_CENTER_QUERY_CMD, 5);
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                log.info("[SECURITY-CENTER] Initialized Windows Security Center baseline: {}",
                        result.getStdout().trim().replace("\r\n", " ; "));
            } else {
                log.debug("[SECURITY-CENTER] SecurityCenter2 query not supported on this Windows SKU.");
            }
        } catch (Exception e) {
            log.warn("[SECURITY-CENTER] Could not initialize Security Center baseline: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${agent.monitors.security-center-rate:${agent.monitors.rate:15000}}")
    public void check() {
        if (!securityCenterEnabled) {
            return;
        }

        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(SECURITY_CENTER_QUERY_CMD, 5);
            if (!result.isSuccess() || result.getStdout().isBlank()) {
                return;
            }

            String[] lines = result.getStdout().split("\\r?\\n");
            boolean anyProductActive = false;
            boolean anyProductDisabled = false;
            StringBuilder disabledDetails = new StringBuilder();

            for (String line : lines) {
                String clean = line.trim();
                if (clean.isEmpty() || !clean.contains("|")) continue;

                String[] parts = clean.split("\\|", 2);
                String name = parts[0];
                long productState = 0;
                try {
                    productState = Long.parseLong(parts[1].trim());
                } catch (Exception ignored) {}

                // In Windows Security Center productState bitmask:
                // Hex format: 0xWXYZZ
                // W: 0 = Other, 1 = Defender, 2 = Third-party
                // X: 0 = Off, 1 = On, 2 = Snoozed
                // ZZ: Up to date state (00 = Up to date, 10 = Out of date)
                long stateNibble = (productState >> 8) & 0xFF;
                boolean isEnabled = (stateNibble & 0x10) != 0;

                if (isEnabled) {
                    anyProductActive = true;
                } else {
                    anyProductDisabled = true;
                    if (disabledDetails.length() > 0) disabledDetails.append(", ");
                    disabledDetails.append(name).append(" (State: ").append(productState).append(")");
                }
            }

            if (anyProductDisabled && !anyProductActive) {
                if (wasAlertDispatched.compareAndSet(false, true)) {
                    log.warn("[SECURITY-CENTER] [THREAT] Antivirus products reported disabled: {}", disabledDetails);
                    try {
                        dispatcher.dispatch(
                                "SecurityCenterAlert",
                                "All registered endpoint antivirus products are DISABLED: " + disabledDetails
                        );
                    } catch (Exception ex) {
                        log.error("[SECURITY-CENTER] Failed to dispatch SecurityCenterAlert", ex);
                    }
                }
            } else if (anyProductActive) {
                if (wasAlertDispatched.compareAndSet(true, false)) {
                    log.info("[SECURITY-CENTER] [RECOVERY] Registered security product is active and protecting.");
                }
            }

        } catch (Exception e) {
            log.error("[SECURITY-CENTER] Error during Security Center status check", e);
        }
    }
}

