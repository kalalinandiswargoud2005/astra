package com.astra.windowsagent;

import com.astra.windowsagent.companion.AstraCompanionClient;
import com.astra.windowsagent.util.CommandRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class WindowsAgentApplication {

    public static void main(String[] args) {
        // Check if started in User Session UI Companion Mode
        boolean isCompanionMode = false;
        String customIpcUrl = "http://127.0.0.1:8082/api/v1/agent/ipc/overlay-stream";

        for (String arg : args) {
            if ("--astra.companion=true".equalsIgnoreCase(arg) || "--overlay-client".equalsIgnoreCase(arg) || "-companion".equalsIgnoreCase(arg)) {
                isCompanionMode = true;
            }
            if (arg.startsWith("--ipc.url=")) {
                customIpcUrl = arg.substring(10);
            }
        }

        System.setProperty("java.awt.headless", "false");

        if (isCompanionMode) {
            AstraCompanionClient companion = new AstraCompanionClient(customIpcUrl);
            companion.start();
            return;
        }

        // Enforce single-instance main agent per system to prevent duplicate processes
        try {
            java.net.ServerSocket lockSocket = new java.net.ServerSocket(58081, 0, java.net.InetAddress.getByName("127.0.0.1"));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lockSocket.close(); } catch (Exception ignored) {}
            }));
        } catch (Exception e) {
            log.warn("[ASTRA-AGENT] Another instance of ASTRA Windows Agent is already running on this endpoint. Exiting duplicate process.");
            System.err.println("[ASTRA-AGENT] Another instance of ASTRA Windows Agent is already running. Exiting.");
            return;
        }

        // Full EDR Windows Service Mode with GUI Overlay capability
        new org.springframework.boot.builder.SpringApplicationBuilder(WindowsAgentApplication.class)
                .headless(false)
                .run(args);
    }

    @Bean
    public ApplicationRunner privilegeLogger() {
        return args -> {
            boolean isAdmin = CommandRunner.isRunningAsAdministrator();
            log.info("=================================================");
            log.info("       ASTRA WINDOWS EDR ENDPOINT AGENT          ");
            log.info("  Privilege Status -> Administrator: {}", isAdmin ? "TRUE" : "FALSE (NON-ADMINISTRATOR)");
            if (!isAdmin) {
                log.warn("  [NOTICE] Agent running without Administrator privileges.");
                log.warn("  [NOTICE] System modifications (RDP, Firewall, Defender) will require elevation.");
            }
            log.info("=================================================");
        };
    }
}
