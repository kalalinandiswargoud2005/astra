package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class HostsMonitor {

    public enum HostsState {
        PRESENT,
        DELETED,
        UNKNOWN
    }

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.hosts.enabled:true}")
    private boolean hostsMonitorEnabled = true;

    @Value("${agent.monitors.hosts.path:}")
    private String configuredHostsPath;

    // Thread-safe state tracking
    private final AtomicReference<String> lastKnownHash = new AtomicReference<>("");
    private final AtomicReference<HostsState> lastKnownState = new AtomicReference<>(HostsState.UNKNOWN);
    private final AtomicLong lastCheckedModified = new AtomicLong(-1L);
    private final AtomicLong lastCheckedLength = new AtomicLong(-1L);

    @PostConstruct
    public void init() {
        if (!hostsMonitorEnabled) {
            log.info("[HOSTS-MONITOR] Hosts file monitor is disabled via configuration.");
            return;
        }

        Path hostsPath = resolveHostsPath();
        File hostsFile = hostsPath.toFile();

        if (hostsFile.exists()) {
            String initialHash = computeFileHash(hostsFile);
            if (!initialHash.isEmpty()) {
                lastKnownHash.set(initialHash);
                lastKnownState.set(HostsState.PRESENT);
                lastCheckedModified.set(hostsFile.lastModified());
                lastCheckedLength.set(hostsFile.length());
                log.info("[HOSTS-MONITOR] Initialized baseline hosts file SHA-256: {} ({})",
                        initialHash, hostsPath);
            } else {
                lastKnownState.set(HostsState.UNKNOWN);
                log.warn("[HOSTS-MONITOR] Hosts file exists at {} but could not be hashed at startup.", hostsPath);
            }
        } else {
            lastKnownState.set(HostsState.DELETED);
            log.warn("[HOSTS-MONITOR] Hosts file not found at {} during startup.", hostsPath);
        }
    }

    /**
     * Periodically monitors the Windows hosts file for modification or deletion.
     */
    @Scheduled(fixedRateString = "${agent.monitors.hosts-rate:${agent.monitors.rate:1500}}")
    public void check() {
        if (!hostsMonitorEnabled) {
            return;
        }

        try {
            Path hostsPath = resolveHostsPath();
            File hostsFile = hostsPath.toFile();

            if (!hostsFile.exists()) {
                // File deletion check
                HostsState prevState = lastKnownState.getAndSet(HostsState.DELETED);
                if (prevState == HostsState.PRESENT) {
                    log.warn("[HOSTS-MONITOR] [THREAT] Windows hosts file was DELETED or MOVED: {}", hostsPath);
                    lastKnownHash.set("");
                    lastCheckedModified.set(-1L);
                    lastCheckedLength.set(-1L);

                    try {
                        dispatcher.dispatch(
                                "HostsFileChanged",
                                "Windows hosts file was deleted or moved (DNS tampering vector): " + hostsPath
                        );
                    } catch (Exception ex) {
                        log.error("[HOSTS-MONITOR] Failed to dispatch HostsFileChanged threat event for deletion", ex);
                    }
                }
                return;
            }

            // Quick metadata check to avoid unnecessary I/O when unchanged
            long currentModified = hostsFile.lastModified();
            long currentLength = hostsFile.length();

            if (lastKnownState.get() == HostsState.PRESENT
                    && currentModified == lastCheckedModified.get()
                    && currentLength == lastCheckedLength.get()
                    && !lastKnownHash.get().isEmpty()) {
                // File metadata untouched; skip redundant hashing
                return;
            }

            // Compute cryptographic hash
            String currentHash = computeFileHash(hostsFile);

            if (currentHash.isEmpty()) {
                // Transient read/lock/permission error: do not alter baseline or fire false alarms
                log.debug("[HOSTS-MONITOR] Skipping check cycle; could not read hosts file (possibly locked/permission denied).");
                return;
            }

            HostsState prevState = lastKnownState.getAndSet(HostsState.PRESENT);
            String prevHash = lastKnownHash.getAndSet(currentHash);
            lastCheckedModified.set(currentModified);
            lastCheckedLength.set(currentLength);

            if (prevState == HostsState.DELETED) {
                // File was restored/re-created
                log.info("[HOSTS-MONITOR] [RECOVERY] Windows hosts file has been RESTORED (Hash: {}).", currentHash);

            } else if (prevState == HostsState.PRESENT && !prevHash.isEmpty() && !currentHash.equalsIgnoreCase(prevHash)) {
                // Content tampering / modification detected
                log.warn("[HOSTS-MONITOR] [THREAT] Windows hosts file was MODIFIED! [Old: {}..., New: {}...]",
                        prevHash.substring(0, Math.min(8, prevHash.length())),
                        currentHash.substring(0, Math.min(8, currentHash.length())));

                try {
                    dispatcher.dispatch(
                            "HostsFileChanged",
                            "Windows hosts file modification detected (DNS Hijack vector). Path: " + hostsPath
                    );
                } catch (Exception ex) {
                    log.error("[HOSTS-MONITOR] Failed to dispatch HostsFileChanged threat event", ex);
                }

            } else if (prevState == HostsState.UNKNOWN) {
                log.info("[HOSTS-MONITOR] Hosts file state established after startup: {}", currentHash);
            }

        } catch (Exception e) {
            log.error("[HOSTS-MONITOR] Error during hosts file integrity verification", e);
        }
    }

    /**
     * Resolves the hosts file Path using configuration or the standard Windows system directory.
     */
    private Path resolveHostsPath() {
        if (configuredHostsPath != null && !configuredHostsPath.isBlank()) {
            return Paths.get(configuredHostsPath.trim());
        }

        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = "C:\\Windows";
        }
        return Paths.get(sysRoot, "System32", "drivers", "etc", "hosts");
    }

    /**
     * Computes the SHA-256 hash of the specified file.
     * Returns an empty string if reading fails so errors are never mistaken for legitimate content.
     */
    private String computeFileHash(File file) {
        try (InputStream is = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.debug("[HOSTS-MONITOR] Could not compute hash for {}: {}", file.getAbsolutePath(), e.getMessage());
            return "";
        }
    }
}
