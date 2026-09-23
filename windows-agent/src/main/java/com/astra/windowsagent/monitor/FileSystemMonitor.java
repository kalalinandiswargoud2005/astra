package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSystemMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.demo.base-dir:C:\\Astra\\Demo}")
    private String demoBaseDir = "C:\\Astra\\Demo";

    @Value("${agent.monitors.filesystem.scan-demo-sandbox:true}")
    private boolean scanDemoSandbox = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "astra-filesystem-watcher");
        t.setDaemon(true);
        return t;
    });

    private volatile WatchService startupWatchService;
    private volatile boolean running = true;

    // Thread-safe deduplication sets
    private final Set<String> alertedStartupFiles = ConcurrentHashMap.newKeySet();
    private final Set<String> knownEncryptedSandboxFiles = ConcurrentHashMap.newKeySet();

    private static final Set<String> SUSPICIOUS_STARTUP_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    ".bat", ".exe", ".vbs", ".ps1", ".cmd", ".js", ".hta", ".dll", ".scr"
            ))
    );

    @PostConstruct
    public void init() {
        executor.submit(this::watchStartupFolder);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        log.info("[FILE-MONITOR] Shutting down FileSystemMonitor...");
        try {
            if (startupWatchService != null) {
                startupWatchService.close();
            }
        } catch (IOException e) {
            log.debug("[FILE-MONITOR] Error closing startup WatchService: {}", e.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /*
     * ============================================================
     * REAL ENDPOINT MONITORING: Startup Folder Watcher
     * ============================================================
     */
    private void watchStartupFolder() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                log.warn("[FILE-MONITOR] APPDATA environment variable not found. Startup folder watch skipped.");
                return;
            }
            Path startupPath = Paths.get(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");

            if (!Files.exists(startupPath)) {
                log.info("[FILE-MONITOR] Startup path does not exist: {}", startupPath);
                return;
            }

            startupWatchService = FileSystems.getDefault().newWatchService();
            startupPath.register(startupWatchService, StandardWatchEventKinds.ENTRY_CREATE);
            log.info("[FILE-MONITOR] [REAL-ENDPOINT] Monitoring Startup folder: {}", startupPath);

            while (running && !Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = startupWatchService.take();
                } catch (ClosedWatchServiceException | InterruptedException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path filename = (Path) event.context();
                        String name = filename.toString().toLowerCase(Locale.ROOT);

                        boolean isSuspicious = SUSPICIOUS_STARTUP_EXTENSIONS.stream().anyMatch(name::endsWith);
                        if (isSuspicious) {
                            String fullPath = startupPath.resolve(filename).toString();
                            if (alertedStartupFiles.add(fullPath)) {
                                log.warn("[FILE-MONITOR] [REAL-ENDPOINT] Suspicious file dropped in Startup: {}", fullPath);
                                try {
                                    dispatcher.dispatch(
                                            "SuspiciousStartup",
                                            "Real file created in Windows Startup directory: " + fullPath
                                    );
                                } catch (Exception dispatchEx) {
                                    alertedStartupFiles.remove(fullPath);
                                    log.error("[FILE-MONITOR] Failed to dispatch SuspiciousStartup alert for {}", fullPath, dispatchEx);
                                }
                            }
                        }
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (Exception e) {
            if (running) {
                log.debug("[FILE-MONITOR] Startup watcher stopped: {}", e.getMessage());
            }
        }
    }

    /*
     * ============================================================
     * SIMULATED SANDBOX MONITORING: ASTRA Demo Base Directory
     * ============================================================
     *
     * Monitors C:\Astra\Demo (or configured base path) and subdirectories.
     * Prevents recursive threat simulation loops by:
     * 1. Deduping observed sandbox files so each artifact is only reported once.
     * 2. Tagging events distinctly as [SIMULATED-SANDBOX].
     * 3. Cleaning up stale entries when demo directories are purged.
     */
    @Scheduled(
            fixedRateString = "${agent.monitors.filesystem.rate:${agent.monitors.rate:1000}}"
    )
    public void scanDemoSandbox() {
        if (!scanDemoSandbox) {
            return;
        }

        try {
            Path demoBase = Paths.get(demoBaseDir);
            if (!Files.exists(demoBase)) {
                knownEncryptedSandboxFiles.clear();
                return;
            }

            Set<String> currentlyFoundFiles = new HashSet<>();

            File[] incidentDirs = demoBase.toFile().listFiles(File::isDirectory);
            if (incidentDirs != null) {
                for (File incDir : incidentDirs) {
                    File attackDir = new File(incDir, "attack");
                    if (attackDir.exists() && attackDir.isDirectory()) {
                        File[] encFiles = attackDir.listFiles((d, name) -> name.endsWith(".encrypted"));
                        if (encFiles != null) {
                            for (File ef : encFiles) {
                                String pathStr = ef.getAbsolutePath();
                                currentlyFoundFiles.add(pathStr);

                                if (knownEncryptedSandboxFiles.add(pathStr)) {
                                    log.info("[FILE-MONITOR] [SIMULATED-SANDBOX] Autonomous detection: Encrypted demo file found: {}", ef.getName());
                                    try {
                                        dispatcher.dispatch(
                                                "SimulatedRansomware",
                                                "Simulated ransomware encryption detected in demo sandbox: " + ef.getName()
                                        );
                                    } catch (Exception dispatchEx) {
                                        knownEncryptedSandboxFiles.remove(pathStr);
                                        log.error("[FILE-MONITOR] Failed to dispatch SimulatedRansomware alert for {}", pathStr, dispatchEx);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Evict purged demo sandbox files so future tests can trigger freshly
            knownEncryptedSandboxFiles.retainAll(currentlyFoundFiles);

        } catch (Exception e) {
            log.debug("[FILE-MONITOR] Sandbox scan exception: {}", e.getMessage());
        }
    }
}
