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

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.eventlog.enabled:true}")
    private boolean eventLogMonitorEnabled = true;

    @Value("${agent.monitors.eventlog.failed-logon-threshold:5}")
    private int failedLogonThreshold = 5;

    @Value("${agent.monitors.eventlog.window-minutes:2}")
    private int windowMinutes = 2;

    @Value("${agent.monitors.eventlog.event-type:EventLogCleared}")
    private String eventType = "EventLogCleared";

    // Tracks the most recent event timestamp or watermark to prevent re-alerting on the same burst
    private final AtomicLong lastAlertedTimestampMs = new AtomicLong(0L);

    @PostConstruct
    public void init() {
        if (!eventLogMonitorEnabled) {
            log.info("[EVENTLOG-MONITOR] Windows Security Event Log monitor is disabled via configuration.");
            return;
        }
        lastAlertedTimestampMs.set(System.currentTimeMillis());
        log.info("[EVENTLOG-MONITOR] Initialized Security Event Log monitor (Event ID 4625). Threshold: {} attempts in {}m, EventType: {}",
                failedLogonThreshold, windowMinutes, eventType);
    }

    /**
     * Periodically monitors Windows Security Event Log for Event ID 4625 (Failed Logon / Brute Force).
     */
    @Scheduled(fixedRateString = "${agent.monitors.eventlog-rate:${agent.monitors.rate:2000}}")
    public void check() {
        if (!eventLogMonitorEnabled) {
            return;
        }

        try {
            String script = String.format(
                    "try { " +
                    "$events = @(Get-WinEvent -FilterHashtable @{LogName='Security'; Id=4625; StartTime=(Get-Date).AddMinutes(-%d)} -ErrorAction Stop); " +
                    "'COUNT=' + $events.Count; " +
                    "if ($events.Count -gt 0) { " +
                    "$sample = $events[0]; " +
                    "'SAMPLE=' + $sample.TimeCreated.Ticks + '::' + $sample.RecordId + '::' + $sample.Properties[5].Value + '::' + $sample.Properties[19].Value + '::' + $sample.Properties[10].Value; " +
                    "} " +
                    "} catch { " +
                    "if ($_.Exception.Message -match 'No events were found') { 'COUNT=0' } else { 'ERROR=' + $_.Exception.Message } " +
                    "}",
                    windowMinutes
            );

            CommandResult result = CommandRunner.runPowerShellWithResult(script, 5);

            if (!result.isSuccess() || result.getStdout().isBlank()) {
                log.debug("[EVENTLOG-MONITOR] PowerShell command failed or returned empty output (exitCode={})",
                        result.getExitCode());
                return;
            }

            String output = result.getStdout().trim();

            if (output.startsWith("ERROR=")) {
                log.debug("[EVENTLOG-MONITOR] Security log query returned error: {}", output);
                return;
            }

            ParsedEventLogResult parsed = parseOutput(output);

            if (parsed == null) {
                return;
            }

            if (parsed.failedCount >= failedLogonThreshold) {
                long now = System.currentTimeMillis();
                long lastAlert = lastAlertedTimestampMs.get();

                // Suppress duplicate alerts for the same burst within the lookback window
                if ((now - lastAlert) >= (windowMinutes * 60L * 1000L)) {
                    lastAlertedTimestampMs.set(now);

                    String contextInfo = buildContextInfo(parsed);
                    log.warn("[EVENTLOG-MONITOR] [THREAT] Excessive failed logons detected: {} failed attempts in last {}m. {}",
                            parsed.failedCount, windowMinutes, contextInfo);

                    try {
                        dispatcher.dispatch(
                                eventType,
                                "Excessive failed logons detected (Brute-Force vector): " + parsed.failedCount + " attempts in " + windowMinutes + "m. " + contextInfo
                        );
                    } catch (Exception dispatchEx) {
                        log.error("[EVENTLOG-MONITOR] Failed to dispatch {} threat event", eventType, dispatchEx);
                    }
                }
            }

        } catch (Exception e) {
            log.error("[EVENTLOG-MONITOR] Error during Security Event Log check", e);
        }
    }

    private record ParsedEventLogResult(int failedCount, String username, String sourceIp, String logonType) {}

    private ParsedEventLogResult parseOutput(String output) {
        int count = 0;
        String username = "Unknown";
        String sourceIp = "Local / Unknown";
        String logonType = "Unknown";

        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("COUNT=")) {
                try {
                    count = Integer.parseInt(trimmed.substring(6).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else if (trimmed.startsWith("SAMPLE=")) {
                String sampleData = trimmed.substring(7).trim();
                String[] parts = sampleData.split("::", -1);
                if (parts.length >= 3 && !parts[2].isBlank()) {
                    username = parts[2].trim();
                }
                if (parts.length >= 4 && !parts[3].isBlank()) {
                    sourceIp = parts[3].trim();
                }
                if (parts.length >= 5 && !parts[4].isBlank()) {
                    logonType = translateLogonType(parts[4].trim());
                }
            }
        }

        return new ParsedEventLogResult(count, username, sourceIp, logonType);
    }

    private String buildContextInfo(ParsedEventLogResult parsed) {
        StringBuilder sb = new StringBuilder();
        if (!"Unknown".equalsIgnoreCase(parsed.username)) {
            sb.append("Target User: ").append(parsed.username).append(", ");
        }
        if (!"Local / Unknown".equalsIgnoreCase(parsed.sourceIp) && !"-".equals(parsed.sourceIp)) {
            sb.append("Source IP: ").append(parsed.sourceIp).append(", ");
        }
        if (!"Unknown".equalsIgnoreCase(parsed.logonType)) {
            sb.append("Logon Type: ").append(parsed.logonType);
        }
        return sb.toString().replaceAll(", $", "");
    }

    private String translateLogonType(String typeCode) {
        return switch (typeCode) {
            case "2" -> "Interactive (Console)";
            case "3" -> "Network (SMB/RPC)";
            case "7" -> "Unlock Workstation";
            case "8" -> "NetworkCleartext (IIS)";
            case "10" -> "RemoteInteractive (RDP)";
            default -> "Type " + typeCode;
        };
    }
}
