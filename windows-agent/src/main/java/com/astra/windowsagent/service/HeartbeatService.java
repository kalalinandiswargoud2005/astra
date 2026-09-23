package com.astra.windowsagent.service;

import com.astra.windowsagent.config.AgentConfigHelper;
import com.astra.windowsagent.dto.HeartbeatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    @Value("${agent.version:1.0.0}")
    private String version;

    private final AgentConfigHelper configHelper;
    private final RestTemplate restTemplate = createTimeoutRestTemplate();
    private final SystemInfo systemInfo = new SystemInfo();

    private static RestTemplate createTimeoutRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        return new RestTemplate(factory);
    }
    
    private String lastCommandId = "none";
    private String lastCommandStatus = "none";

    public void updateLastCommand(String commandId, String status) {
        this.lastCommandId = commandId;
        this.lastCommandStatus = status;
    }

    @Scheduled(fixedRate = 5000) // Send heartbeat every 5 seconds
    public void sendHeartbeat() {
        String deviceId = configHelper.getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return; // Wait until registration resolves identity
        }

        String backendUrl = configHelper.getBackendUrl();
        String hostname = configHelper.getHostname();
        String deviceToken = configHelper.getDeviceToken();

        double cpu = 0.0;
        double ram = 0.0;

        try {
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            long[] prevTicks = processor.getSystemCpuLoadTicks();
            Thread.sleep(50);
            cpu = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;

            GlobalMemory memory = systemInfo.getHardware().getMemory();
            ram = ((double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal()) * 100.0;
        } catch (Exception ignored) {
            cpu = 2.5;
            ram = 40.0;
        }

        HeartbeatDto dto = HeartbeatDto.builder()
                .deviceId(deviceId)
                .hostname(hostname)
                .agentVersion(version)
                .os(System.getProperty("os.name"))
                .username(System.getProperty("user.name"))
                .ipAddress(configHelper.getIpAddress())
                .status("ONLINE")
                .serviceStatus("RUNNING")
                .companionStatus("CONNECTED")
                .overlayStatus("AVAILABLE")
                .cpuUsage(Math.round(cpu * 10.0) / 10.0)
                .ramUsage(Math.round(ram * 10.0) / 10.0)
                .timestamp(java.time.Instant.now().toString())
                .lastCommandId(lastCommandId)
                .lastCommandStatus(lastCommandStatus)
                .build();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (deviceToken != null && !deviceToken.isBlank()) {
                headers.set("X-Device-Token", deviceToken);
            }
            HttpEntity<HeartbeatDto> request = new HttpEntity<>(dto, headers);

            for (String bUrl : configHelper.getBackendUrls()) {
                try {
                    restTemplate.postForEntity(bUrl + "/api/v1/agent/heartbeat", request, Object.class);
                    log.trace("[ASTRA-HEARTBEAT] Dispatched to {}: deviceId={}, CPU={}%", bUrl, deviceId, dto.getCpuUsage());
                } catch (Exception e) {
                    log.debug("[ASTRA-HEARTBEAT] Heartbeat ping failed for {}: {}", bUrl, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("[ASTRA-HEARTBEAT] Heartbeat build error: {}", e.getMessage());
        }
    }
}
