package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class CPUMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.cpu.enabled:true}")
    private boolean cpuMonitorEnabled = true;

    @Value("${agent.monitors.cpu.threshold-percent:85.0}")
    private double cpuThresholdPercent = 85.0;

    // Number of consecutive breach cycles required before firing an alert (prevents transient spike alarms)
    @Value("${agent.monitors.cpu.sustained-cycles:3}")
    private int sustainedBreachCycles = 3;

    private final AtomicBoolean isAlertActive = new AtomicBoolean(false);
    private final AtomicInteger consecutiveBreachCount = new AtomicInteger(0);

    // OSHI processor handle for reliable hardware metrics
    private final SystemInfo systemInfo = new SystemInfo();
    private volatile CentralProcessor processor;
    private volatile long[] prevTicks;

    @PostConstruct
    public void init() {
        if (!cpuMonitorEnabled) {
            log.info("[CPU-MONITOR] CPU monitor is disabled via configuration.");
            return;
        }

        try {
            processor = systemInfo.getHardware().getProcessor();
            prevTicks = processor.getSystemCpuLoadTicks();
            log.info("[CPU-MONITOR] Initialized CPU monitor. Threshold: {}%, Sustained Cycles: {}",
                    cpuThresholdPercent, sustainedBreachCycles);
        } catch (Exception e) {
            log.warn("[CPU-MONITOR] Could not initialize OSHI processor metrics: {}", e.getMessage());
        }
    }

    /**
     * Periodically monitors CPU utilization and detects abnormal sustained high CPU usage.
     */
    @Scheduled(fixedRateString = "${agent.monitors.cpu-rate:${agent.monitors.rate:${agent.monitor.rate:3000}}}")
    public void check() {
        if (!cpuMonitorEnabled) {
            return;
        }

        try {
            double currentCpuUsage = getCurrentCpuUsagePercent();

            // Ignore uninitialized or invalid metric readings (-1.0)
            if (currentCpuUsage < 0.0) {
                log.debug("[CPU-MONITOR] Skipping cycle; CPU metric unavailable.");
                return;
            }

            double roundedCpu = Math.round(currentCpuUsage * 10.0) / 10.0;

            if (roundedCpu >= cpuThresholdPercent) {
                int count = consecutiveBreachCount.incrementAndGet();
                log.debug("[CPU-MONITOR] CPU breach cycle {}/{} (Usage: {}%)",
                        count, sustainedBreachCycles, roundedCpu);

                if (count >= sustainedBreachCycles) {
                    if (isAlertActive.compareAndSet(false, true)) {
                        log.warn("[CPU-MONITOR] [THREAT] Sustained high CPU utilization detected: {}% for {} consecutive cycles",
                                roundedCpu, count);

                        try {
                            dispatcher.dispatch(
                                    "HighCPU",
                                    "Abnormal sustained high CPU utilization: " + roundedCpu + "% (Threshold: " + cpuThresholdPercent + "%)"
                            );
                        } catch (Exception ex) {
                            log.error("[CPU-MONITOR] Failed to dispatch HighCPU threat event", ex);
                        }
                    }
                }
            } else {
                // CPU has returned below threshold
                consecutiveBreachCount.set(0);

                if (isAlertActive.compareAndSet(true, false)) {
                    log.info("[CPU-MONITOR] [RECOVERY] CPU utilization normalized: {}% (Below threshold: {}%)",
                            roundedCpu, cpuThresholdPercent);
                }
            }

        } catch (Exception e) {
            log.error("[CPU-MONITOR] Error during CPU usage check", e);
        }
    }

    /**
     * Obtains the current system-wide CPU usage percentage (0.0 to 100.0).
     * Uses OSHI ticks with fallback to com.sun.management.OperatingSystemMXBean.
     */
    private double getCurrentCpuUsagePercent() {
        // 1. Primary: OSHI tick comparison
        if (processor != null && prevTicks != null) {
            try {
                double load = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
                prevTicks = processor.getSystemCpuLoadTicks();
                if (load >= 0.0 && load <= 100.0) {
                    return load;
                }
            } catch (Exception e) {
                log.debug("[CPU-MONITOR] OSHI CPU calculation failed: {}", e.getMessage());
            }
        }

        // 2. Fallback: JVM Platform MXBean
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean != null) {
                double load = osBean.getCpuLoad() * 100.0;
                if (load >= 0.0) {
                    return load;
                }
            }
        } catch (Exception e) {
            log.debug("[CPU-MONITOR] MXBean CPU query failed: {}", e.getMessage());
        }

        return -1.0;
    }
}
