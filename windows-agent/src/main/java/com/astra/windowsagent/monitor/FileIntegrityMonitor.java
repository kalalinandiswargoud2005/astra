package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileIntegrityMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.fim.enabled:true}")
    private boolean fimEnabled = true;

    @Value("${agent.fim.directories:${agent.demo.base-dir:C:\\Astra\\Demo}}")
    private String configuredDirectories = "C:\\Astra\\Demo";

    // In-memory SHA-256 baseline: normalized file path -> SHA-256 hex string
    private final Map<String, String> fileBaseline = new ConcurrentHashMap<>();

    // Track paths where violations were already reported to avoid alert storms
    private final Set<String> alertedViolations = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        if (!fimEnabled) {
            log.info("[FIM] File Integrity Monitor is disabled via configuration.");
            return;
        }
        buildInitialBaseline();
    }

    @PreDestroy
    public void destroy() {
        running = false;
        fileBaseline.clear();
        alertedViolations.clear();
        log.info("[FIM] File Integrity Monitor stopped and baseline cache cleared.");
    }

    /**
     * Periodic scan verifying file integrity against the SHA-256 baseline.
     */
    @Scheduled(fixedRateString = "${agent.fim.rate:${agent.monitors.fim-rate:${agent.monitor.rate:10000}}}")
    public void check() {
        if (!fimEnabled || !running) {
            return;
        }

        if (!initialized.get()) {
            buildInitialBaseline();
            return;
        }

        try {
            List<Path> targetDirs = getTargetDirectories();
            Set<String> currentFiles = new HashSet<>();

            for (Path dir : targetDirs) {
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    continue;
                }

                try (Stream<Path> stream = Files.walk(dir, 5)) {
                    stream.filter(Files::isRegularFile).forEach(file -> {
                        String pathStr = file.toAbsolutePath().normalize().toString();
                        currentFiles.add(pathStr);

                        try {
                            String currentHash = computeSha256(file);
                            String baselineHash = fileBaseline.get(pathStr);

                            if (baselineHash == null) {
                                // 1. New file creation detected after baseline was established
                                String alertKey = "NEW:" + pathStr;
                                if (alertedViolations.add(alertKey)) {
                                    handleIntegrityEvent(
                                            pathStr,
                                            "NEW_FILE_CREATED",
                                            "Integrity Event: New file created: " + file.getFileName() + " [Hash: " + currentHash + "]"
                                    );
                                }
                                fileBaseline.put(pathStr, currentHash);

                            } else if (!baselineHash.equalsIgnoreCase(currentHash)) {
                                // 2. File content tampering / modification detected
                                String alertKey = "MODIFIED:" + pathStr + ":" + currentHash;
                                if (alertedViolations.add(alertKey)) {
                                    handleIntegrityEvent(
                                            pathStr,
                                            "FILE_MODIFIED",
                                            "Integrity Violation: Content modified for " + file.getFileName()
                                                    + " [Original: " + baselineHash.substring(0, 8) + "..., Current: " + currentHash.substring(0, 8) + "...]"
                                    );
                                }
                                fileBaseline.put(pathStr, currentHash);
                            }
                        } catch (Exception ex) {
                            log.debug("[FIM] Could not compute hash for {}: {}", pathStr, ex.getMessage());
                        }
                    });
                } catch (IOException e) {
                    log.debug("[FIM] Directory walk interrupted for {}: {}", dir, e.getMessage());
                }
            }

            // 3. Detect deleted files that were in the baseline
            for (String recordedPath : new ArrayList<>(fileBaseline.keySet())) {
                if (!currentFiles.contains(recordedPath)) {
                    String alertKey = "DELETED:" + recordedPath;
                    if (alertedViolations.add(alertKey)) {
                        handleIntegrityEvent(
                                recordedPath,
                                "FILE_DELETED",
                                "Integrity Event: Monitored file deleted: " + Paths.get(recordedPath).getFileName()
                        );
                    }
                    fileBaseline.remove(recordedPath);
                }
            }

        } catch (Exception e) {
            log.error("[FIM] Integrity scan error", e);
        }
    }

    /**
     * Builds initial SHA-256 cryptographic baseline for all files in configured directories.
     */
    private synchronized void buildInitialBaseline() {
        try {
            List<Path> targetDirs = getTargetDirectories();
            int fileCount = 0;

            for (Path dir : targetDirs) {
                if (!Files.exists(dir)) {
                    continue;
                }

                try (Stream<Path> stream = Files.walk(dir, 5)) {
                    List<Path> files = stream.filter(Files::isRegularFile).toList();
                    for (Path file : files) {
                        try {
                            String hash = computeSha256(file);
                            fileBaseline.put(file.toAbsolutePath().normalize().toString(), hash);
                            fileCount++;
                        } catch (Exception e) {
                            log.debug("[FIM] Baseline skip for {}: {}", file, e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    log.debug("[FIM] Initial baseline walk skipped for {}: {}", dir, e.getMessage());
                }
            }

            initialized.set(true);
            log.info("[FIM] Initialized SHA-256 baseline for {} monitored files across target paths: {}",
                    fileCount, configuredDirectories);

        } catch (Exception e) {
            log.warn("[FIM] Could not construct initial baseline: {}", e.getMessage());
        }
    }

    /**
     * Dispatches integrity events to ThreatDispatcher with clear Real vs Simulated labeling.
     */
    private void handleIntegrityEvent(String filePath, String eventType, String message) {
        boolean isSimulatedSandbox = isSandboxPath(filePath);
        String label = isSimulatedSandbox ? "[SIMULATED-FIM]" : "[REAL-FIM]";

        log.warn("[FIM] {} {} on {}", label, eventType, filePath);

        try {
            dispatcher.dispatch(
                    "FileIntegrityViolation",
                    label + " " + message + " (Path: " + filePath + ")"
            );
        } catch (Exception e) {
            log.error("[FIM] Failed to dispatch FileIntegrityViolation for {}", filePath, e);
        }
    }

    /**
     * Identifies if a path belongs to an active demo/sandbox attack simulation.
     */
    private boolean isSandboxPath(String pathStr) {
        String normalized = pathStr.toLowerCase(Locale.ROOT);
        return normalized.contains("\\demo\\") ||
               normalized.contains("/demo/") ||
               normalized.contains("\\attack\\") ||
               normalized.endsWith(".encrypted") ||
               normalized.contains("manifest.txt");
    }

    /**
     * Parses comma-separated directory list into valid Path objects.
     */
    private List<Path> getTargetDirectories() {
        if (configuredDirectories == null || configuredDirectories.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(configuredDirectories.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Paths::get)
                .toList();
    }

    /**
     * Computes SHA-256 digest of a given file.
     */
    private String computeSha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file, StandardOpenOption.READ)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
