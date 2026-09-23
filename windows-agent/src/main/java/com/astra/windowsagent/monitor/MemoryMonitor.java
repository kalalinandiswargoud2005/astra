package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.memory.enabled:true}")
    private boolean memoryMonitorEnabled = true;

    @Value("${agent.monitors.memory.threshold-percent:90.0}")
    private double memoryThresholdPercent = 90.0;

    @Value("${agent.monitors.memory.min-available-mb:512}")
    private long minAvailableMb = 512L;

    // Number of consecutive breach cycles required before firing an alert
    @Value("${agent.monitors.memory.sustained-cycles:3}")
    private int sustainedBreachCycles = 3;

    private final AtomicBoolean isAlertActive = new AtomicBoolean(false);
    private final AtomicInteger consecutiveBreachCount = new AtomicInteger(0);

    // OSHI hardware abstraction handle for memory metrics
    private final SystemInfo systemInfo = new SystemInfo();
    private volatile GlobalMemory memory;

    @PostConstruct
    public void init() {
        if (!memoryMonitorEnabled) {
            log.info("[MEMORY-MONITOR] Memory monitor is disabled via configuration.");
            return;
        }

        try {
            memory = systemInfo.getHardware().getMemory();
            log.info("[MEMORY-MONITOR] Initialized Memory monitor. Threshold: {}%, Min Free: {}MB, Sustained Cycles: {}",
                    memoryThresholdPercent, minAvailableMb, sustainedBreachCycles);
        } catch (Exception e) {
            log.warn("[MEMORY-MONITOR] Could not initialize OSHI memory metrics: {}", e.getMessage());
        }
    }

    /**
     * Periodically monitors RAM usage and detects abnormal sustained memory exhaustion.
     */
    @Scheduled(fixedRateString = "${agent.monitors.memory-rate:${agent.monitors.rate:${agent.monitor.rate:3000}}}")
    public void check() {
        if (!memoryMonitorEnabled) {
            return;
        }

        try {
            MemorySnapshot snapshot = getMemorySnapshot();

            // Ignore uninitialized or invalid metric readings
            if (snapshot == null || snapshot.totalBytes() <= 0) {
                log.debug("[MEMORY-MONITOR] Skipping cycle; memory metrics unavailable.");
                return;
            }

            double usagePercent = snapshot.usagePercent();
            long availableMb = snapshot.availableBytes() / (1024 * 1024);
            double roundedUsage = Math.round(usagePercent * 10.0) / 10.0;

            boolean isBreached = roundedUsage >= memoryThresholdPercent || availableMb < minAvailableMb;

            if (isBreached) {
                int count = consecutiveBreachCount.incrementAndGet();
                log.debug("[MEMORY-MONITOR] Memory breach cycle {}/{} (Usage: {}%, Free: {}MB)",
                        count, sustainedBreachCycles, roundedUsage, availableMb);

                if (count >= sustainedBreachCycles) {
                    if (isAlertActive.compareAndSet(false, true)) {
                        log.warn("[MEMORY-MONITOR] [THREAT] Sustained memory exhaustion detected: {}% used (Free: {}MB) for {} consecutive cycles",
                                roundedUsage, availableMb, count);

                        try {
                            dispatcher.dispatch(
                                    "MemoryExhaustion",
                                    "Abnormal sustained memory exhaustion: " + roundedUsage + "% used (" + availableMb + "MB free)"
                            );
                        } catch (Exception ex) {
                            log.error("[MEMORY-MONITOR] Failed to dispatch MemoryExhaustion threat event", ex);
                        }
                    }
                }
            } else {
                // Memory has returned to normal operational limits
                consecutiveBreachCount.set(0);

                if (isAlertActive.compareAndSet(true, false)) {
                    log.info("[MEMORY-MONITOR] [RECOVERY] Memory usage normalized: {}% used (Free: {}MB)",
                            roundedUsage, availableMb);
                }
            }

        } catch (Exception e) {
            log.error("[MEMORY-MONITOR] Error during memory check", e);
        }
    }

    private record MemorySnapshot(long totalBytes, long availableBytes) {
        public double usagePercent() {
            if (totalBytes <= 0) return -1.0;
            return ((double) (totalBytes - availableBytes) / totalBytes) * 100.0;
        }
    }

    /**
     * Obtains the current physical memory snapshot.
     * Uses OSHI GlobalMemory with fallback to JVM Platform MXBean.
     */
    private MemorySnapshot getMemorySnapshot() {
        // 1. Primary: OSHI GlobalMemory
        if (memory != null) {
            try {
                long total = memory.getTotal();
                long available = memory.getAvailable();
                if (total > 0) {
                    return new MemorySnapshot(total, available);
                }
            } catch (Exception e) {
                log.debug("[MEMORY-MONITOR] OSHI memory calculation failed: {}", e.getMessage());
            }
        }

        // 2. Fallback: JVM Platform MXBean
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean != null) {
                long total = osBean.getTotalMemorySize();
                long free = osBean.getFreeMemorySize();
                if (total > 0) {
                    return new MemorySnapshot(total, free);
                }
            }
        } catch (Exception e) {
            log.debug("[MEMORY-MONITOR] MXBean memory query failed: {}", e.getMessage());
        }

        return null;
    }
}
