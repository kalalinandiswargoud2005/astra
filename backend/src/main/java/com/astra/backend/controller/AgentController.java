package com.astra.backend.controller;

import com.astra.backend.entity.Device;
import com.astra.backend.entity.DeviceCommand;
import com.astra.backend.entity.Incident;
import com.astra.backend.repository.DeviceCommandRepository;
import com.astra.backend.repository.DeviceRepository;
import com.astra.backend.repository.IncidentRepository;
import com.astra.backend.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final DeviceRepository deviceRepository;
    private final IncidentRepository incidentRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final com.astra.backend.service.ThreatCatalogService threatCatalogService;
    private final com.astra.backend.service.CommandDispatchService commandDispatchService;
    private final DeviceCommandRepository commandRepository;

    private UUID parseUuidSafe(Object obj) {
        if (obj == null || obj.toString().isBlank()) return null;
        try {
            return UUID.fromString(obj.toString());
        } catch (Exception e) {
            try {
                return UUID.nameUUIDFromBytes(obj.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /**
     * Automatic Idempotent First-Start Device Registration.
     * Persistent across restarts using Hardware ID, Hostname, or persistent Device UUID.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> payload) {
        UUID clientDeviceId = parseUuidSafe(payload.get("deviceId"));
        String hostname = (String) payload.getOrDefault("hostname", 
                payload.getOrDefault("deviceName", "Unknown-Node"));
        String os = (String) payload.getOrDefault("os", 
                payload.getOrDefault("windowsVersion", System.getProperty("os.name")));
        String ip = (String) payload.getOrDefault("ipAddress", "127.0.0.1");
        String agentVersion = (String) payload.getOrDefault("agentVersion", "1.0.0");
        String hardwareId = (String) payload.get("hardwareId");
        String macAddress = (String) payload.get("macAddress");
        String clientToken = (String) payload.get("deviceToken");

        log.info("[ASTRA-REGISTRATION] Incoming registration request: Hostname={}, IP={}, HWID={}, ClientUUID={}",
                hostname, ip, hardwareId, clientDeviceId);

        Device device = null;

        // 1. Check by explicit Device UUID if provided
        if (clientDeviceId != null) {
            device = deviceRepository.findById(clientDeviceId).orElse(null);
        }

        // 2. Check by stable Hardware ID if present
        if (device == null && hardwareId != null && !hardwareId.isBlank()) {
            device = deviceRepository.findByHardwareId(hardwareId).orElse(null);
        }

        // 3. Check by Hostname fallback
        if (device == null && hostname != null && !hostname.isBlank()) {
            device = deviceRepository.findByHostname(hostname).orElse(null);
        }

        // 4. Create new device if first time seeing this endpoint
        boolean isNew = false;
        if (device == null) {
            device = new Device();
            device.setId(clientDeviceId != null ? clientDeviceId : UUID.randomUUID());
            device.setName(hostname);
            device.setCreatedAt(LocalDateTime.now());
            isNew = true;
        }

        device.setHostname(hostname);
        device.setName(hostname);
        device.setType("WINDOWS_AGENT");
        device.setOs(os);
        device.setIpAddress(ip);
        device.setAgentVersion(agentVersion);
        if (hardwareId != null && !hardwareId.isBlank()) device.setHardwareId(hardwareId);
        if (macAddress != null && !macAddress.isBlank()) device.setMacAddress(macAddress);
        device.setStatus("ONLINE");
        device.setHealth("EXCELLENT");
        device.setLastSeen(LocalDateTime.now());
        device.setLastHeartbeat(LocalDateTime.now());

        if (clientToken != null && !clientToken.isBlank()) {
            device.setDeviceToken(clientToken);
        } else if (device.getDeviceToken() == null || device.getDeviceToken().isBlank()) {
            device.setDeviceToken("ast_" + UUID.randomUUID().toString().replace("-", ""));
        }

        deviceRepository.save(device);
        log.info("[ASTRA-REGISTRATION] Device {} successfully (DeviceID={}, Hostname={}, Token={})",
                isNew ? "CREATED" : "UPDATED", device.getId(), device.getHostname(), device.getDeviceToken());

        webSocketPublisher.broadcastDeviceStatus(device);

        return ResponseEntity.ok(Map.of(
                "status", "registered",
                "deviceId", device.getId().toString(),
                "deviceToken", device.getDeviceToken(),
                "hostname", device.getHostname(),
                "registeredAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, String>> receiveTelemetry(@RequestBody Map<String, Object> payload) {
        webSocketPublisher.broadcastTelemetry(payload);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * Endpoint Heartbeat (Every 10-15s)
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(
            @RequestHeader(value = "X-Device-Token", required = false) String tokenHeader,
            @RequestBody(required = false) Map<String, Object> payload) {
        
        try {
            if (payload != null && payload.containsKey("deviceId")) {
                UUID deviceId = parseUuidSafe(payload.get("deviceId"));
                if (deviceId == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "INVALID_DEVICE_ID"));
                }

                String hostname = (String) payload.getOrDefault("hostname", "Unknown Device");
                String status = (String) payload.getOrDefault("status", "ONLINE");
                String username = (String) payload.getOrDefault("username", "");
                String companionStatus = (String) payload.getOrDefault("companionStatus", "CONNECTED");
                String overlayStatus = (String) payload.getOrDefault("overlayStatus", "AVAILABLE");
                String ipAddress = (String) payload.getOrDefault("ipAddress", "");
                String agentVersion = (String) payload.getOrDefault("agentVersion", "1.0.0");
                
                Double cpu = payload.get("cpuUsage") instanceof Number ? ((Number) payload.get("cpuUsage")).doubleValue() : null;
                Double ram = payload.get("ramUsage") instanceof Number ? ((Number) payload.get("ramUsage")).doubleValue() : null;

                Device device = deviceRepository.findById(deviceId).orElseGet(() -> {
                    Device d = new Device();
                    d.setId(deviceId);
                    d.setName(hostname);
                    d.setHostname(hostname);
                    d.setType("WINDOWS_AGENT");
                    d.setIpAddress(!ipAddress.isBlank() ? ipAddress : "127.0.0.1");
                    d.setHealth("EXCELLENT");
                    d.setCreatedAt(LocalDateTime.now());
                    return d;
                });

                if (device.getCreatedAt() == null) {
                    device.setCreatedAt(LocalDateTime.now());
                }

                // Token authentication & sync
                if (tokenHeader != null && !tokenHeader.isBlank()) {
                    if (device.getDeviceToken() == null || device.getDeviceToken().isBlank()) {
                        device.setDeviceToken(tokenHeader);
                    }
                }

                boolean wasOffline = "OFFLINE".equalsIgnoreCase(device.getStatus());

                device.setStatus(status != null && !status.isBlank() ? status : "ONLINE");
                device.setLastSeen(LocalDateTime.now());
                device.setLastHeartbeat(LocalDateTime.now());
                if (!ipAddress.isBlank()) device.setIpAddress(ipAddress);
                if (!agentVersion.isBlank()) device.setAgentVersion(agentVersion);
                if (cpu != null) device.setCpuUsage(cpu);
                if (ram != null) device.setRamUsage(ram);
                if (!username.isBlank()) device.setUsername(username);
                device.setCompanionStatus(companionStatus != null ? companionStatus : "CONNECTED");
                device.setOverlayStatus(overlayStatus != null ? overlayStatus : "AVAILABLE");
                deviceRepository.save(device);

                if (wasOffline) {
                    log.info("[ASTRA-HEARTBEAT] Device {} is back ONLINE", device.getName());
                    webSocketPublisher.broadcastDeviceStatus(device);
                }

                Map<String, Object> telemetryMap = new java.util.HashMap<>();
                telemetryMap.put("deviceId", deviceId.toString());
                telemetryMap.put("hostname", hostname != null ? hostname : "Unknown");
                telemetryMap.put("cpuUsage", cpu != null ? cpu : 0.0);
                telemetryMap.put("ramUsage", ram != null ? ram : 0.0);
                telemetryMap.put("status", device.getStatus());
                telemetryMap.put("companionStatus", device.getCompanionStatus());
                telemetryMap.put("overlayStatus", device.getOverlayStatus());
                telemetryMap.put("timestamp", LocalDateTime.now().toString());

                webSocketPublisher.broadcastTelemetry(telemetryMap);
                webSocketPublisher.broadcastCommandResult(deviceId, String.format("[HEARTBEAT] %s | CPU: %.1f%% | RAM: %.1f%% | Status: ONLINE | Agent v%s", hostname, cpu != null ? cpu : 0.0, ram != null ? ram : 0.0, agentVersion));
            }
            return ResponseEntity.ok(Map.of("status", "alive"));
        } catch (Exception e) {
            log.error("[ASTRA-HEARTBEAT-ERROR] Failed to process heartbeat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "internal_error"));
        }
    }

    @PostMapping("/incident")
    public ResponseEntity<Map<String, String>> reportIncident(
            @RequestHeader(value = "X-Device-Token", required = false) String tokenHeader,
            @RequestBody Map<String, Object> payload) {
        
        UUID deviceId = parseUuidSafe(payload.get("deviceId"));
        String threatId = (String) payload.getOrDefault("threatId", "UNKNOWN");
        String hostname = (String) payload.get("hostname");
        String status = (String) payload.getOrDefault("status", "ACTIVE");
        
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());

        // Target naming: prioritize hostname with deviceId fallback
        if (hostname != null && !hostname.isBlank()) {
            incident.setTarget(hostname.trim());
        } else if (deviceId != null) {
            incident.setTarget(deviceRepository.findById(deviceId).map(Device::getName).orElse(deviceId.toString()));
        } else {
            incident.setTarget("Target Laptop");
        }

        incident.setStatus(status != null && !status.isBlank() ? status : "ACTIVE");

        // Parse client detection timestamp to maintain exact forensic timeline sync
        Object rawTs = payload.get("timestamp");
        if (rawTs != null && !rawTs.toString().isBlank()) {
            try {
                java.time.Instant instant = java.time.Instant.parse(rawTs.toString());
                incident.setCreatedAt(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
            } catch (Exception e1) {
                try {
                    incident.setCreatedAt(java.time.LocalDateTime.parse(rawTs.toString()));
                } catch (Exception e2) {
                    incident.setCreatedAt(java.time.LocalDateTime.now());
                }
            }
        } else {
            incident.setCreatedAt(java.time.LocalDateTime.now());
        }
        
        threatCatalogService.getThreatById(threatId).ifPresentOrElse(
            catalog -> {
                incident.setName(catalog.getThreatName());
                incident.setType(catalog.getCategory());
                incident.setSeverity(catalog.getSeverity());
            },
            () -> {
                incident.setName("Threat Detection: " + threatId);
                incident.setType("Endpoint Alert");
                incident.setSeverity("HIGH");
            }
        );

        incidentRepository.save(incident);
        webSocketPublisher.broadcastNewThreat(incident);
        log.info("[INCIDENT] New incident recorded: ID={}, Name={}, Target={}, Status={}, CreatedAt={}", 
                incident.getId(), incident.getName(), incident.getTarget(), incident.getStatus(), incident.getCreatedAt());

        return ResponseEntity.ok(Map.of("status", "reported", "incidentId", incident.getId().toString()));
    }

    /**
     * Authenticated Command Queue Polling.
     * Fetches PENDING commands for exact deviceId, marks them DELIVERED atomically.
     */
    @GetMapping("/commands/{deviceId}")
    public ResponseEntity<?> getPendingCommands(
            @PathVariable UUID deviceId,
            @RequestHeader(value = "X-Device-Token", required = false) String tokenHeader) {
        
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DEVICE_NOT_FOUND"));
        }

        Device device = deviceOpt.get();
        if (tokenHeader != null && !tokenHeader.isBlank() && device.getDeviceToken() != null) {
            if (!device.getDeviceToken().equals(tokenHeader)) {
                log.warn("[ASTRA-AUTH] Unauthorized command poll for device {} with token {}", deviceId, tokenHeader);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED_DEVICE_TOKEN"));
            }
        }

        List<DeviceCommand> list = commandRepository.findByDeviceIdAndStatus(deviceId, "PENDING");
        if (!list.isEmpty()) {
            for (DeviceCommand cmd : list) {
                log.info("[ASTRA-CMD] DELIVERING command to device {}: commandId={}, commandType={}",
                        deviceId, cmd.getId(), cmd.getCommandType());
                cmd.setStatus("DELIVERED");
                webSocketPublisher.broadcastCommandEvent(Map.of(
                    "commandId", cmd.getId().toString(),
                    "deviceId", deviceId.toString(),
                    "commandType", cmd.getCommandType(),
                    "status", "DELIVERED",
                    "timestamp", LocalDateTime.now().toString()
                ));
            }
            commandRepository.saveAll(list);
            commandRepository.flush();
        }
        return ResponseEntity.ok(list);
    }

    /**
     * Command Execution & Verification Result Reporting.
     */
    @PostMapping("/commands/{commandId}/result")
    public ResponseEntity<Map<String, String>> reportCommandResult(
            @PathVariable UUID commandId, 
            @RequestHeader(value = "X-Device-Token", required = false) String tokenHeader,
            @RequestBody Map<String, String> payload) {
        
        commandRepository.findById(commandId).ifPresent(cmd -> {
            String rawStatus = payload.getOrDefault("status", "COMPLETED");
            String status = "FAILED".equalsIgnoreCase(rawStatus) ? "FAILED" : 
                            "REJECTED".equalsIgnoreCase(rawStatus) ? "REJECTED" : "COMPLETED";
            cmd.setStatus(status);
            String result = payload.getOrDefault("details", payload.getOrDefault("result", ""));
            cmd.setResult(result);
            cmd.setExecutedAt(LocalDateTime.now());
            commandRepository.save(cmd);

            log.info("[ASTRA-CMD] RESULT_RECEIVED commandId={}, deviceId={}, status={}, result={}", 
                    commandId, cmd.getDeviceId(), status, result);

            // Broadcast command result to device terminal and global command topic
            webSocketPublisher.broadcastCommandResult(cmd.getDeviceId(), result);
            webSocketPublisher.broadcastCommandEvent(Map.of(
                "commandId", commandId.toString(),
                "deviceId", cmd.getDeviceId().toString(),
                "commandType", cmd.getCommandType(),
                "status", status,
                "result", result,
                "timestamp", LocalDateTime.now().toString()
            ));

            // Only auto-resolve if this was explicitly a final resolution command
            if (cmd.getIncidentId() != null && "COMPLETED".equals(status) &&
                    ("FINAL_RESOLUTION".equalsIgnoreCase(cmd.getCommandType()) || "FINAL_VERIFICATION".equalsIgnoreCase(cmd.getCommandType()))) {
                incidentRepository.findById(cmd.getIncidentId()).ifPresent(incident -> {
                    var pending = commandRepository.findByIncidentId(cmd.getIncidentId())
                            .stream().filter(c -> "PENDING".equals(c.getStatus()) || "DELIVERED".equals(c.getStatus())).count();
                    if (pending == 0) {
                        incident.setStatus("RESOLVED");
                        incidentRepository.save(incident);
                        webSocketPublisher.broadcastNewThreat(incident);
                        log.info("[INCIDENT] Incident {} resolved via verified endpoint final resolution", incident.getId());
                    }
                });
            }
        });
        
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    /**
     * Download the latest windows-agent.jar for OTA remote upgrades and deployment.
     */
    @GetMapping("/binary/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadLatestAgentBinary() {
        java.io.File[] potentialPaths = new java.io.File[] {
            new java.io.File("ASTRA_USB_DEPLOYMENT/windows-agent.jar"),
            new java.io.File("../ASTRA_USB_DEPLOYMENT/windows-agent.jar"),
            new java.io.File("windows-agent/target/windows-agent-1.0.0.jar"),
            new java.io.File("../windows-agent/target/windows-agent-1.0.0.jar")
        };

        for (java.io.File file : potentialPaths) {
            if (file.exists() && file.length() > 0) {
                org.springframework.core.io.FileSystemResource resource = new org.springframework.core.io.FileSystemResource(file);
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"windows-agent.jar\"")
                        .contentLength(file.length())
                        .body(resource);
            }
        }

        return ResponseEntity.notFound().build();
    }
}
