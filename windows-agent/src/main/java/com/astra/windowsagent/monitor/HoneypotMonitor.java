package com.astra.windowsagent.monitor;

import com.astra.windowsagent.dispatcher.ThreatDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoneypotMonitor {

    private final ThreatDispatcher dispatcher;

    @Value("${agent.monitors.honeypot.enabled:true}")
    private boolean honeypotMonitorEnabled = true;

    private final AtomicBoolean alertTriggered = new AtomicBoolean(false);
    private File honeypotFile;
    private long lastModified = 0;

    @PostConstruct
    public void init() {
        if (!honeypotMonitorEnabled) return;
        
        try {
            // Create honeypot in public documents
            File publicDocs = new File(System.getenv("PUBLIC") + "\\Documents");
            if (!publicDocs.exists()) {
                publicDocs.mkdirs();
            }
            honeypotFile = new File(publicDocs, "Confidential_Budget_Q4.docx");
            
            if (!honeypotFile.exists()) {
                try (FileWriter writer = new FileWriter(honeypotFile)) {
                    writer.write("DO NOT MODIFY THIS FILE. IT IS A SECURITY DECOY.");
                }
            }
            lastModified = honeypotFile.lastModified();
            // Hide the file
            Files.setAttribute(honeypotFile.toPath(), "dos:hidden", true);
            log.info("[HONEYPOT-MONITOR] Initialized decoy at {}", honeypotFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("[HONEYPOT-MONITOR] Failed to initialize honeypot", e);
        }
    }

    @Scheduled(fixedRateString = "${agent.monitors.honeypot-rate:2000}")
    public void check() {
        if (!honeypotMonitorEnabled || honeypotFile == null) return;

        try {
            if (!honeypotFile.exists()) {
                if (alertTriggered.compareAndSet(false, true)) {
                    log.warn("[HONEYPOT-MONITOR] [THREAT] Ransomware Honeypot DELETED: {}", honeypotFile.getName());
                    dispatcher.dispatch("RansomwareHoneypot", "Ransomware Honeypot deleted: " + honeypotFile.getName());
                }
            } else {
                long currentModified = honeypotFile.lastModified();
                if (currentModified > lastModified) {
                    if (alertTriggered.compareAndSet(false, true)) {
                        log.warn("[HONEYPOT-MONITOR] [THREAT] Ransomware Honeypot MODIFIED: {}", honeypotFile.getName());
                        dispatcher.dispatch("RansomwareHoneypot", "Ransomware Honeypot modified: " + honeypotFile.getName());
                    }
                    lastModified = currentModified;
                }
            }
        } catch (Exception e) {
            log.error("[HONEYPOT-MONITOR] Error checking honeypot", e);
        }
    }
}
