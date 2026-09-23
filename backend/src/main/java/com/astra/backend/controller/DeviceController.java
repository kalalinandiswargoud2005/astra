package com.astra.backend.controller;

import com.astra.backend.entity.Device;
import com.astra.backend.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final com.astra.backend.service.CommandDispatchService commandDispatchService;

    @GetMapping
    public ResponseEntity<List<Device>> getDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/command")
    public ResponseEntity<Map<String, String>> sendCommand(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        
        Device device = deviceService.getDeviceById(id);
        if (device == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET DEVICE NOT FOUND — ACTION NOT EXECUTED"));
        }

        if (!"ONLINE".equalsIgnoreCase(device.getStatus())) {
            log.warn("[ASTRA-DISPATCH] Rejected command for device {} because status is {}", device.getName(), device.getStatus());
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET OFFLINE — ACTION NOT EXECUTED"));
        }

        String rawCommandType = payload.getOrDefault("commandType", "UNKNOWN");
        String target = payload.get("target");
        
        // Normalize command type
        String commandType = rawCommandType;
        if ("EXECUTE_WOW_FEATURE".equalsIgnoreCase(rawCommandType)) {
            if ("GHOST_TYPER".equalsIgnoreCase(target)) {
                commandType = "SIMULATE_GHOST_TYPER";
            } else if ("MATRIX_OVERLAY".equalsIgnoreCase(target) || "SHOW_MATRIX_OVERLAY".equalsIgnoreCase(target)) {
                commandType = "SHOW_MATRIX_OVERLAY";
            } else if ("CLEAR_MATRIX".equalsIgnoreCase(target)) {
                commandType = "CLEAR_MATRIX";
            } else if ("HACKER_SKULL".equalsIgnoreCase(target) || "SHOW_HACKER_SKULL".equalsIgnoreCase(target)) {
                commandType = "SHOW_HACKER_SKULL";
            } else if ("RADAR_BEACON".equalsIgnoreCase(target) || "SHOW_RADAR_BEACON".equalsIgnoreCase(target)) {
                commandType = "SHOW_RADAR_BEACON";
            } else if ("GLITCH_BREACH".equalsIgnoreCase(target) || "SHOW_GLITCH_BREACH".equalsIgnoreCase(target)) {
                commandType = "SHOW_GLITCH_BREACH";
            } else if ("HEX_SHIELD".equalsIgnoreCase(target) || "SHOW_HEX_SHIELD".equalsIgnoreCase(target)) {
                commandType = "SHOW_HEX_SHIELD";
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "UNSUPPORTED_DEMO_FEATURE: " + target));
            }
        } else if ("RECOVERY_STEP".equalsIgnoreCase(rawCommandType) || "EXECUTE_DYNAMIC_SCRIPT".equalsIgnoreCase(rawCommandType)) {
            String lowerTarget = (target != null ? target : "").toLowerCase();
            if (lowerTarget.contains("snipe") || lowerTarget.contains("rogue threat window") || lowerTarget.contains("close rogue") || lowerTarget.contains("rogue window")) {
                commandType = "SNIPE_ROGUE_WINDOW";
            } else if (lowerTarget.contains("lock") && (lowerTarget.contains("workstation") || lowerTarget.contains("laptop") || lowerTarget.contains("screen") || lowerTarget.contains("endpoint"))) {
                commandType = "LOCK_WORKSTATION";
            } else if (lowerTarget.contains("terminate") && (lowerTarget.contains("port") || lowerTarget.contains("listener") || lowerTarget.contains("rat") || lowerTarget.contains("backdoor") || lowerTarget.contains("sever rat"))) {
                commandType = "STOP_TEST_LISTENER";
            } else if (lowerTarget.contains("registry") || lowerTarget.contains("persistence") || lowerTarget.contains("purge persistence")) {
                commandType = "RESTORE_TEST_REGISTRY";
            } else if (lowerTarget.contains("quarantine") || lowerTarget.contains("staged malware") || lowerTarget.contains("stager") || lowerTarget.contains("sandbox artifact")) {
                commandType = "QUARANTINE_TEST_FILE";
            } else if (lowerTarget.contains("firewall") || lowerTarget.contains("host firewall")) {
                commandType = "RESTORE_FIREWALL";
            } else if (lowerTarget.contains("defender") || lowerTarget.contains("antivirus") || lowerTarget.contains("realtime")) {
                commandType = "ENABLE_DEFENDER_REALTIME";
            } else if (lowerTarget.contains("rdp") || lowerTarget.contains("remote desktop")) {
                commandType = "DISABLE_RDP";
            } else if (lowerTarget.contains("rollback") || lowerTarget.contains("decrypt") || lowerTarget.contains("restore file")) {
                commandType = "RESTORE_TEST_FILE";
            } else if (lowerTarget.contains("exfiltration")) {
                commandType = "STOP_TEST_EXFILTRATION";
            } else if (lowerTarget.contains("process") || lowerTarget.contains("kill") || lowerTarget.contains("freeze")) {
                commandType = "STOP_TEST_PROCESS";
            } else if (lowerTarget.contains("scan") || lowerTarget.contains("integrity")) {
                commandType = "FULL_DEFENDER_SCAN";
            } else if (lowerTarget.contains("final verification") || lowerTarget.contains("baseline confirmation") || lowerTarget.contains("threat neutralization")) {
                commandType = "FINAL_VERIFICATION";
            } else {
                commandType = "RECOVERY_STEP";
            }
        } else if ("RESTART_AGENT".equalsIgnoreCase(rawCommandType) || "RESTART".equalsIgnoreCase(rawCommandType)) {
            commandType = "RESTART_AGENT";
        } else if ("UPDATE_AGENT".equalsIgnoreCase(rawCommandType) || "UPDATE".equalsIgnoreCase(rawCommandType) || "OTA_UPDATE".equalsIgnoreCase(rawCommandType)) {
            commandType = "UPDATE_AGENT";
        }
        
        UUID incidentId = null;
        if (payload.containsKey("incidentId")) {
            try {
                incidentId = UUID.fromString(payload.get("incidentId"));
            } catch (Exception ignored) {}
        }

        String params = payload.get("parameters");
        if (params == null || params.isBlank() || !params.trim().startsWith("{")) {
            java.util.Map<String, Object> pMap = new java.util.HashMap<>();
            if (target != null && !target.isBlank()) {
                pMap.put("target", target);
            }
            try {
                params = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(pMap);
            } catch (Exception e) {
                params = "{}";
            }
        }
        
        log.info("[ASTRA-DISPATCH] Queuing command for Device: {} ({}), Type: {}, Target: {}", 
                device.getName(), id, commandType, target);

        com.astra.backend.entity.DeviceCommand cmd = commandDispatchService.queueCommand(id, incidentId, commandType, params);
        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "command", commandType,
                "deviceId", id.toString(),
                "commandId", cmd != null && cmd.getId() != null ? cmd.getId().toString() : ""
        ));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, String>> restartAgent(@PathVariable UUID id) {
        Device device = deviceService.getDeviceById(id);
        if (device == null || !"ONLINE".equalsIgnoreCase(device.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET OFFLINE OR NOT FOUND"));
        }
        commandDispatchService.queueCommand(id, null, "RESTART_AGENT", "{}");
        return ResponseEntity.ok(Map.of("status", "Restart Command Queued", "deviceId", id.toString()));
    }

    @PostMapping("/{id}/update")
    public ResponseEntity<Map<String, String>> updateAgent(@PathVariable UUID id) {
        Device device = deviceService.getDeviceById(id);
        if (device == null || !"ONLINE".equalsIgnoreCase(device.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET OFFLINE OR NOT FOUND"));
        }
        commandDispatchService.queueCommand(id, null, "UPDATE_AGENT", "{}");
        return ResponseEntity.ok(Map.of("status", "OTA Update Command Queued", "deviceId", id.toString()));
    }

    @PostMapping("/broadcast-update")
    public ResponseEntity<Map<String, Object>> broadcastUpdateAllAgents() {
        List<Device> onlineDevices = deviceService.getAllDevices().stream()
                .filter(d -> "ONLINE".equalsIgnoreCase(d.getStatus()))
                .toList();
        
        for (Device device : onlineDevices) {
            commandDispatchService.queueCommand(device.getId(), null, "UPDATE_AGENT", "{}");
        }

        return ResponseEntity.ok(Map.of(
                "status", "Broadcast Queued",
                "targetedCount", onlineDevices.size(),
                "message", "OTA Update command queued for " + onlineDevices.size() + " online endpoint(s)."
        ));
    }
}
