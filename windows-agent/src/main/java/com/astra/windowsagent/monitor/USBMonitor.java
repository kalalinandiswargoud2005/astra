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

@Slf4j
@Component
@RequiredArgsConstructor
public class USBMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.usb.enabled:true}")
    private boolean usbMonitorEnabled = true;

    // Optional comma-separated allowlist of trusted USB serials/hardware IDs
    @Value("${agent.monitors.usb.allowlist:}")
    private String usbAllowlist = "";

    // Thread-safe tracking of connected USB devices (ID -> Human-readable label)
    private final Map<String, String> knownUsbDevices = new ConcurrentHashMap<>();

    private static final String USB_QUERY_CMD =
            "Get-CimInstance Win32_DiskDrive | Where-Object InterfaceType -eq 'USB' | " +
            "Select-Object -Property DeviceID, PNPDeviceID, Model, SerialNumber | " +
            "Format-Table -HideTableHeaders DeviceID, PNPDeviceID, Model";

    @PostConstruct
    public void init() {
        if (!usbMonitorEnabled) {
            log.info("[USB-MONITOR] USB storage monitor is disabled via configuration.");
            return;
        }

        Map<String, String> initialDevices = queryUsbDevices();
        if (initialDevices != null) {
            knownUsbDevices.putAll(initialDevices);
            log.info("[USB-MONITOR] Initialized baseline connected USB devices: {}", knownUsbDevices.values());
        } else {
            log.warn("[USB-MONITOR] Could not establish initial USB baseline (query failed).");
        }
    }

    /**
     * Periodically monitors USB storage connection and removal.
     */
    @Scheduled(fixedRateString = "${agent.monitors.usb-rate:${agent.monitors.rate:1000}}")
    public void check() {
        if (!usbMonitorEnabled) {
            return;
        }

        try {
            Map<String, String> currentDevices = queryUsbDevices();

            // Never assume 0 devices if query failed or encountered OS error
            if (currentDevices == null) {
                log.debug("[USB-MONITOR] Skipping check cycle; USB device query failed.");
                return;
            }

            // 1. Detect newly inserted USB devices
            for (Map.Entry<String, String> entry : currentDevices.entrySet()) {
                String deviceKey = entry.getKey();
                String deviceLabel = entry.getValue();

                if (!knownUsbDevices.containsKey(deviceKey)) {
                    boolean isAuthorized = isDeviceAuthorized(deviceKey, deviceLabel);
                    String threatMessage = isAuthorized
                            ? "Authorized USB storage device connected: " + deviceLabel
                            : "USB storage device connected: " + deviceLabel;

                    log.warn("[USB-MONITOR] [THREAT] New USB storage device detected: {} (ID: {})", deviceLabel, deviceKey);

                    try {
                        dispatcher.dispatch("USBInserted", threatMessage);
                        knownUsbDevices.put(deviceKey, deviceLabel);
                    } catch (Exception dispatchEx) {
                        log.error("[USB-MONITOR] Failed to dispatch USBInserted threat event for {}", deviceLabel, dispatchEx);
                    }
                }
            }

            // 2. Detect removed/disconnected USB devices
            for (Map.Entry<String, String> entry : new ArrayList<>(knownUsbDevices.entrySet())) {
                String deviceKey = entry.getKey();
                String deviceLabel = entry.getValue();

                if (!currentDevices.containsKey(deviceKey)) {
                    log.info("[USB-MONITOR] [DISCONNECT] USB storage device removed: {} (ID: {})", deviceLabel, deviceKey);
                    knownUsbDevices.remove(deviceKey);
                }
            }

        } catch (Exception e) {
            log.error("[USB-MONITOR] Error during USB device scan", e);
        }
    }

    /**
     * Queries Windows for currently connected USB mass storage drives.
     * Returns:
     * - Map of deviceKey -> deviceDescription on success
     * - Empty map if no USB devices are connected
     * - null if the command failed, timed out, or threw an error
     */
    private Map<String, String> queryUsbDevices() {
        try {
            CommandResult result = CommandRunner.runPowerShellWithResult(USB_QUERY_CMD, 5);

            if (!result.isSuccess()) {
                log.debug("[USB-MONITOR] PowerShell query failed (exitCode={})", result.getExitCode());
                return null;
            }

            String output = result.getStdout().trim();
            Map<String, String> devices = new HashMap<>();

            if (output.isEmpty()) {
                return devices;
            }

            for (String line : output.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] tokens = trimmed.split("\\s+", 3);
                String deviceId = tokens.length > 0 ? tokens[0].trim() : "UNKNOWN_DEV";
                String pnpId = tokens.length > 1 ? tokens[1].trim() : deviceId;
                String model = tokens.length > 2 ? tokens[2].trim() : "Generic USB Disk";

                // Unique identifier key prioritizing stable PNPDeviceID
                String uniqueKey = !pnpId.isEmpty() ? pnpId : deviceId;
                String readableLabel = model + " (" + deviceId + ")";

                devices.put(uniqueKey, readableLabel);
            }

            return devices;

        } catch (Exception e) {
            log.debug("[USB-MONITOR] Failed to query USB drives: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the device matches any configured trusted allowlist pattern.
     */
    private boolean isDeviceAuthorized(String deviceId, String label) {
        if (usbAllowlist == null || usbAllowlist.isBlank()) {
            return false;
        }

        String idLower = deviceId.toLowerCase(Locale.ROOT);
        String labelLower = label.toLowerCase(Locale.ROOT);

        return Arrays.stream(usbAllowlist.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> idLower.contains(pattern) || labelLower.contains(pattern));
    }
}
