package com.astra.windowsagent.service;

import com.astra.windowsagent.config.AgentConfigHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRegistration {

    private final AgentConfigHelper configHelper;
    private final RestTemplate restTemplate = createTimeoutRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean registered = false;

    private static RestTemplate createTimeoutRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerDeviceOnStartup() {
        Thread regThread = new Thread(this::executeRegistrationLoop, "Astra-Registration-Thread");
        regThread.setDaemon(true);
        regThread.start();
    }

    public void executeRegistrationLoop() {
        int attempt = 1;
        int delayMs = 2000;

        while (!registered) {
            java.util.List<String> urls = configHelper.getBackendUrls();
            boolean anySuccess = false;

            for (String backendUrl : urls) {
                try {
                    String registerUrl = backendUrl + "/api/v1/agent/register";
                    log.info("[ASTRA-REGISTRATION] Connecting to SOC backend: URL={} (Attempt {})", registerUrl, attempt);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("hostname", configHelper.getHostname());
                    payload.put("deviceName", configHelper.getHostname());
                    payload.put("ipAddress", configHelper.getIpAddress());
                    payload.put("macAddress", configHelper.getMacAddress());
                    payload.put("hardwareId", configHelper.getHardwareId());
                    payload.put("os", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
                    payload.put("agentVersion", "1.0.0");

                    if (configHelper.getDeviceId() != null && !configHelper.getDeviceId().isBlank()) {
                        payload.put("deviceId", configHelper.getDeviceId());
                    }
                    if (configHelper.getDeviceToken() != null && !configHelper.getDeviceToken().isBlank()) {
                        payload.put("deviceToken", configHelper.getDeviceToken());
                    }

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                    ResponseEntity<String> response = restTemplate.postForEntity(registerUrl, request, String.class);
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode json = objectMapper.readTree(response.getBody());
                        String deviceId = json.path("deviceId").asText();
                        String deviceToken = json.path("deviceToken").asText();

                        configHelper.saveIdentity(deviceId, deviceToken, backendUrl);
                        anySuccess = true;

                        log.info("================================================================================");
                        log.info(" [ASTRA-AGENT] SUCCESSFULLY REGISTERED WITH SOC: {}", backendUrl);
                        log.info("  ├─ Device ID    : {}", deviceId);
                        log.info("  ├─ Hostname     : {}", configHelper.getHostname());
                        log.info("  └─ IP Address   : {}", configHelper.getIpAddress());
                        log.info("================================================================================");
                    }
                } catch (Exception e) {
                    log.debug("[ASTRA-REGISTRATION] Backend {} not reachable: {}", backendUrl, e.getMessage());
                }
            }

            if (anySuccess) {
                registered = true;
                return;
            }

            attempt++;
            try {
                Thread.sleep(delayMs);
                delayMs = Math.min(delayMs * 2, 10000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public boolean isRegistered() {
        return registered || configHelper.isRegistered();
    }
}
