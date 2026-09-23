package com.astra.backend.service;

import com.astra.backend.entity.Device;
import com.astra.backend.entity.DeviceCommand;
import com.astra.backend.entity.Incident;
import com.astra.backend.repository.DeviceCommandRepository;
import com.astra.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandDispatchService {

    private final DeviceCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final com.astra.backend.websocket.WebSocketPublisher webSocketPublisher;

    private UUID resolveDeviceId(String target) {
        if (target == null || target.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(target);
            if (deviceRepository.existsById(uuid)) {
                return uuid;
            }
        } catch (Exception ignored) {}

        // Look up by hostname / name
        return deviceRepository.findByName(target)
                .map(Device::getId)
                .orElse(null);
    }

    public void dispatchCommandsForIncident(Incident incident) {
        if (incident.getTarget() == null || incident.getTarget().isEmpty()) {
            log.warn("[ASTRA-DISPATCH] Incident {} has no target device. Cannot dispatch commands.", incident.getId());
            return;
        }

        UUID deviceId = resolveDeviceId(incident.getTarget());
        if (deviceId == null) {
            // Fallback: check first online device
            var onlineDevice = deviceRepository.findAll().stream()
                    .filter(d -> "ONLINE".equalsIgnoreCase(d.getStatus()))
                    .findFirst();
            if (onlineDevice.isPresent()) {
                deviceId = onlineDevice.get().getId();
            } else {
                log.warn("[ASTRA-DISPATCH] Could not resolve device UUID for target: {}", incident.getTarget());
                return;
            }
        }

        log.info("[ASTRA-DISPATCH] Dispatching recovery commands for incident: {}, Device UUID: {}", incident.getId(), deviceId);

        String name = incident.getName() != null ? incident.getName().toLowerCase() : "";
        String type = incident.getType() != null ? incident.getType().toLowerCase() : "";

        if (name.contains("ransomware") || type.contains("ransomware")) {
            queueCommand(deviceId, incident.getId(), "STOP_TEST_PROCESS", "{\"target\":\"ping.exe\"}");
            queueCommand(deviceId, incident.getId(), "QUARANTINE_TEST_FILE", "{}");
            queueCommand(deviceId, incident.getId(), "RESTORE_TEST_FILE", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("registry") || type.contains("persistence")) {
            queueCommand(deviceId, incident.getId(), "RESTORE_TEST_REGISTRY", "{}");
            queueCommand(deviceId, incident.getId(), "REMOVE_TEST_PERSISTENCE", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("backdoor") || name.contains("port") || type.contains("c2")) {
            queueCommand(deviceId, incident.getId(), "STOP_TEST_LISTENER", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("firewall") || type.contains("defense evasion")) {
            queueCommand(deviceId, incident.getId(), "RESTORE_FIREWALL", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("antivirus") || name.contains("defender")) {
            queueCommand(deviceId, incident.getId(), "ENABLE_REALTIME", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("rdp") || name.contains("remote desktop")) {
            queueCommand(deviceId, incident.getId(), "DISABLE_RDP", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else if (name.contains("exfiltration") || type.contains("exfiltration")) {
            queueCommand(deviceId, incident.getId(), "STOP_TEST_EXFILTRATION", "{}");
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        } else {
            // General defensive baseline
            queueCommand(deviceId, incident.getId(), "FINAL_VERIFICATION", "{}");
        }
    }

    public DeviceCommand queueCommand(UUID deviceId, UUID incidentId, String commandType, String params) {
        DeviceCommand cmd = DeviceCommand.builder()
                .deviceId(deviceId)
                .incidentId(incidentId)
                .commandType(commandType)
                .parameters(params != null ? params : "{}")
                .status("PENDING")
                .build();
        cmd = commandRepository.saveAndFlush(cmd);
        log.info("[ASTRA-DISPATCH] Queued command: deviceId={}, commandId={}, incidentId={}, commandType={}",
                deviceId, cmd.getId(), incidentId, commandType);

        try {
            if (webSocketPublisher != null) {
                webSocketPublisher.broadcastCommandEvent(java.util.Map.of(
                        "commandId", cmd.getId().toString(),
                        "deviceId", deviceId.toString(),
                        "commandType", commandType,
                        "status", "QUEUED",
                        "timestamp", java.time.LocalDateTime.now().toString()
                ));
            }
        } catch (Exception e) {
            log.warn("[ASTRA-DISPATCH] Failed to broadcast queued command event: {}", e.getMessage());
        }

        return cmd;
    }
}
