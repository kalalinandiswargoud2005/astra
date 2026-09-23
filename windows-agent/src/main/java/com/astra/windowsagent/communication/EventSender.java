package com.astra.windowsagent.communication;

import com.astra.windowsagent.config.AgentConfigHelper;
import com.astra.windowsagent.dto.ThreatEventDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSender {

    private final AgentConfigHelper configHelper;
    private final RestTemplate restTemplate = createTimeoutRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File cacheFile = new File(System.getenv("ProgramData") + "\\Astra\\offline_events.json");

    // Deduplication map: tracks ThreatID -> Timestamp (millis)
    private final Map<String, Long> deduplicationCache = new ConcurrentHashMap<>();
    private static final long DEDUPLICATION_WINDOW_MS = 5000; // 5 seconds

    private static RestTemplate createTimeoutRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    private static final int MAX_OFFLINE_QUEUE_SIZE = 500;
    private final Queue<ThreatEventDto> offlineQueue = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        if (!cacheFile.getParentFile().exists()) {
            cacheFile.getParentFile().mkdirs();
        }
        loadOfflineEvents();
    }

    private synchronized void loadOfflineEvents() {
        if (cacheFile.exists()) {
            try {
                List<ThreatEventDto> saved = objectMapper.readValue(cacheFile, new TypeReference<List<ThreatEventDto>>() {});
                offlineQueue.addAll(saved);
                log.info("[ASTRA-EVENT-QUEUE] Loaded {} offline events from disk", saved.size());
            } catch (Exception e) {
                log.error("[ASTRA-EVENT-QUEUE] Failed to load offline events from {}", cacheFile.getAbsolutePath(), e);
            }
        }
    }

    private synchronized void saveOfflineEvents() {
        try {
            List<ThreatEventDto> toSave = new ArrayList<>(offlineQueue);
            objectMapper.writeValue(cacheFile, toSave);
        } catch (IOException e) {
            log.error("[ASTRA-EVENT-QUEUE] Failed to save offline events to disk", e);
        }
    }

    public void sendEvent(String threatId, String details) {
        long now = System.currentTimeMillis();
        Long lastSeen = deduplicationCache.get(threatId);
        
        // Event Deduplication logic: Drop if identical threat occurred within 5 seconds
        if (lastSeen != null && (now - lastSeen < DEDUPLICATION_WINDOW_MS)) {
            log.debug("[ASTRA-EVENT] Deduplicating rapid event: {}", threatId);
            return;
        }
        deduplicationCache.put(threatId, now);

        String deviceId = configHelper.getDeviceId();
        String hostname = configHelper.getHostname();
        String deviceToken = configHelper.getDeviceToken();

        ThreatEventDto event = ThreatEventDto.builder()
                .deviceId(deviceId != null && !deviceId.isBlank() ? deviceId : "pending")
                .hostname(hostname)
                .timestamp(Instant.now().toString())
                .threatId(threatId)
                .status("ACTIVE")
                .severity("HIGH")
                .metadata(Map.of("user", System.getProperty("user.name"), "details", details != null ? details : ""))
                .build();

        if (deviceId == null || deviceId.isBlank() || "pending".equals(deviceId)) {
            log.info("[ASTRA-EVENT] Device not yet registered. Buffering incident for post-registration replay: {}", threatId);
            queueOfflineEvent(event);
            return;
        }

        log.info("[ASTRA-EVENT] Reporting Incident: ThreatID={}, DeviceID={}, Hostname={}", threatId, deviceId, hostname);

        boolean delivered = deliverEvent(event, deviceToken);
        if (!delivered) {
            queueOfflineEvent(event);
        }
    }

    private boolean deliverEvent(ThreatEventDto event, String deviceToken) {
        String backendUrl = configHelper.getBackendUrl();
        if (backendUrl == null || backendUrl.isBlank()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (deviceToken != null && !deviceToken.isBlank()) {
                headers.set("X-Device-Token", deviceToken);
            }
            HttpEntity<ThreatEventDto> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(backendUrl + "/api/v1/agent/incident", request, Map.class);
            log.info("[ASTRA-EVENT] Incident reported to backend successfully ({})", event.getThreatId());
            return true;
        } catch (Exception e) {
            log.warn("[ASTRA-EVENT] Backend unreachable ({}): {}. Queueing for offline replay.",
                    event.getThreatId(), e.getMessage());
            return false;
        }
    }

    private void queueOfflineEvent(ThreatEventDto event) {
        if (offlineQueue.size() >= MAX_OFFLINE_QUEUE_SIZE) {
            offlineQueue.poll();
        }
        offlineQueue.offer(event);
        log.info("[ASTRA-EVENT-QUEUE] Buffered offline incident. Queue depth: {}", offlineQueue.size());
        saveOfflineEvents();
    }

    @Scheduled(fixedDelayString = "${agent.communication.flush-rate:1000}")
    public void flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return;

        String currentDeviceId = configHelper.getDeviceId();
        if (currentDeviceId == null || currentDeviceId.isBlank() || "pending".equals(currentDeviceId)) {
            return;
        }

        String deviceToken = configHelper.getDeviceToken();
        int drained = 0;
        boolean changed = false;

        while (!offlineQueue.isEmpty()) {
            ThreatEventDto pendingEvent = offlineQueue.peek();
            if (pendingEvent == null) break;

            if ("pending".equals(pendingEvent.getDeviceId()) || pendingEvent.getDeviceId() == null || pendingEvent.getDeviceId().isBlank()) {
                pendingEvent.setDeviceId(currentDeviceId);
            }

            boolean delivered = deliverEvent(pendingEvent, deviceToken);
            if (delivered) {
                offlineQueue.poll();
                drained++;
                changed = true;
            } else {
                break;
            }
        }

        if (changed) {
            saveOfflineEvents();
        }

        if (drained > 0) {
            log.info("[ASTRA-EVENT-QUEUE] Successfully flushed {} offline events. Remaining in buffer: {}",
                    drained, offlineQueue.size());
        }
    }

    // Clean up deduplication cache periodically to prevent memory leaks
    @Scheduled(fixedDelay = 60000)
    public void cleanupDeduplicationCache() {
        long now = System.currentTimeMillis();
        deduplicationCache.entrySet().removeIf(entry -> (now - entry.getValue() > DEDUPLICATION_WINDOW_MS));
    }
}
