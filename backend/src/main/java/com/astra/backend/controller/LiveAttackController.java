package com.astra.backend.controller;

import com.astra.backend.entity.Device;
import com.astra.backend.entity.Incident;
import com.astra.backend.repository.IncidentRepository;
import com.astra.backend.service.CommandDispatchService;
import com.astra.backend.service.DeviceService;
import com.astra.backend.service.RecoveryService;
import com.astra.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/live-attacks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Transactional
public class LiveAttackController {

    private final DeviceService deviceService;
    private final CommandDispatchService commandDispatchService;
    private final IncidentRepository incidentRepository;
    private final RecoveryService recoveryService;
    private final NotificationService notificationService;

    @PostMapping("/{attackType}")
    public ResponseEntity<Map<String, String>> triggerLiveAttack(
            @PathVariable String attackType,
            @RequestParam(required = false) String target) {
        List<Device> devices = deviceService.getAllDevices();
        if (devices.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET OFFLINE — ACTION NOT EXECUTED (No devices registered)"));
        }
        
        Device targetDevice = null;
        if (target != null && !target.isBlank()) {
            for (Device d : devices) {
                if (d.getId().toString().equalsIgnoreCase(target) || d.getName().equalsIgnoreCase(target)) {
                    targetDevice = d;
                    break;
                }
            }
        }
        if (targetDevice == null) {
            targetDevice = devices.stream().filter(d -> "ONLINE".equalsIgnoreCase(d.getStatus())).findFirst().orElse(devices.get(0));
        }

        if (!"ONLINE".equalsIgnoreCase(targetDevice.getStatus())) {
            log.warn("[LIVE_ATTACK] Rejected attack command for device {} because status is {}", targetDevice.getName(), targetDevice.getStatus());
            return ResponseEntity.badRequest().body(Map.of("error", "TARGET OFFLINE — ACTION NOT EXECUTED"));
        }
        
        // 1. Generate a corresponding incident in the backend
        Incident incident = new Incident();
        incident.setTarget(targetDevice.getName());
        incident.setStatus("ACTIVE");
        incident.setSeverity("HIGH");

        String immediateAction = "";
        String recoveryWorkflow = "";

        switch (attackType) {
            case "ASTRA_END_TO_END_SAFE_TEST":
            case "SIMULATED_RANSOMWARE":
                incident.setName("Simulated Ransomware File Encryption");
                incident.setType("Ransomware");
                incident.setSeverity("CRITICAL");
                incident.setAiExplanation("AI heuristic engine detected rapid file extension modification (.encrypted) in C:\\Astra\\ValuableData.");
                immediateAction = "Isolate Endpoint & Freeze Malicious Process";
                recoveryWorkflow = "Stop Simulated Attack Process, Decrypt & Rollback Files, Run Threat Quick Scan, Restore Network Connectivity, Reconcile System Baseline";
                break;
            case "DARKSIDE_PAYLOAD":
            case "SIMULATED_DARKSIDE":
                incident.setName("DarkSide Rogue Payload & Window Injection");
                incident.setType("Ransomware");
                incident.setSeverity("CRITICAL");
                incident.setAiExplanation("Autonomous AI engine intercepted unauthorized rogue window spawn attempting persistence injection.");
                immediateAction = "Freeze Malicious Sandbox Vector";
                recoveryWorkflow = "Snipe & Force-Close Rogue Threat Window, Purge Persistence Registry Hooks, Quarantine Staged Malware Artifacts, Enforce Microsoft Defender & Host Firewall, Final Verification & Clean Baseline Confirmation";
                break;
            case "STEALTH_RAT_BACKDOOR":
            case "SIMULATED_STEALTH_RAT":
                incident.setName("Stealth RAT Backdoor & Workstation Breach");
                incident.setType("C2 Beacon");
                incident.setSeverity("CRITICAL");
                incident.setAiExplanation("Unauthorized loopback socket listener intercepted on TCP 44444 indicating interactive RAT breach.");
                immediateAction = "Isolate Backdoor Socket & Sound Alert";
                recoveryWorkflow = "Terminate Rogue Port & Sever RAT Listener, Remotely Lock Target Workstation, Enforce Remote Desktop (RDP) Lockdown, Quick Security Integrity Scan, Final Threat Neutralization Verification";
                break;
            case "HACKER_WALLPAPER":
            case "SKULL_WALLPAPER":
                incident.setName("Hacker Skull Ransomware Hijack Overlay");
                incident.setType("Ransomware");
                incident.setSeverity("HIGH");
                incident.setAiExplanation("Visual desktop configuration attack simulated in safe sandbox.");
                immediateAction = "Render Defense Containment Barrier";
                recoveryWorkflow = "Quarantine Staged Artifacts, Restore Clean Visual Baseline, Reconcile Endpoint Health";
                break;
            case "GHOST_TYPER":
            case "KEYSTROKE_INJECTION":
                incident.setName("Ghost-Typer Keystroke Injection Attack");
                incident.setType("Injection");
                incident.setSeverity("HIGH");
                incident.setAiExplanation("Autonomous EDR intercepted rapid unauthorized keystroke injection script.");
                immediateAction = "Freeze Interactive Session Stream";
                recoveryWorkflow = "Terminate Injection Process, Reset Terminal Session, Audit Keyboard Hooks";
                break;
            case "MATRIX_RAIN":
            case "MATRIX_HUD":
                incident.setName("Matrix Cyber Security HUD Simulation");
                incident.setType("Visualization");
                incident.setSeverity("MEDIUM");
                incident.setAiExplanation("Full-screen live cyber telemetry stream and containment HUD.");
                immediateAction = "Engage Matrix Defense Stream";
                recoveryWorkflow = "Audit Realtime Monitored Subsystems, Clear Matrix Telemetry HUD";
                break;
            case "CYBER_GLITCH":
            case "MEMORY_CORRUPTION":
                incident.setName("Zero-Day Memory Corruption & Buffer Overflow");
                incident.setType("Zero Day");
                incident.setSeverity("CRITICAL");
                incident.setAiExplanation("Heuristic memory guard detected heap spray violation and abnormal stack corruption.");
                immediateAction = "Enforce Memory Stack Protection";
                recoveryWorkflow = "Purge Injected Memory Buffers, Trigger Fast Memory Audit, Restore Clean Baseline";
                break;
            case "RADAR_BEACON":
            case "C2_RADAR":
                incident.setName("C2 Beacon Intercept & Radar Telemetry");
                incident.setType("C2 Beacon");
                incident.setSeverity("HIGH");
                incident.setAiExplanation("Continuous radar scan detected active beaconing socket on endpoint.");
                immediateAction = "Acknowledge Radar Intercept";
                recoveryWorkflow = "Terminate Intercepted Beacon, Lock Peripheral Ports, Verify Clean Network State";
                break;
            case "HEX_SHIELD":
            case "DEFENSE_MATRIX":
                incident.setName("Hexagonal Cyber Shield Enforcement");
                incident.setType("Defense");
                incident.setSeverity("LOW");
                incident.setAiExplanation("Real-time automated verification of all 5 security subsystem shields.");
                immediateAction = "Engage Level 5 Cyber Defense Shield";
                recoveryWorkflow = "Verify Host Firewall, Verify Defender Realtime, Reconcile Baseline Integrity";
                break;
            case "REGISTRY_HIJACK":
                incident.setName("Registry Hijack (Task Manager Disabled)");
                incident.setType("Persistence");
                incident.setAiExplanation("A rogue process modified the registry to disable Task Manager (DisableTaskMgr).");
                immediateAction = "Identify Registry Change";
                recoveryWorkflow = "RESTORE_REGISTRY";
                break;
            case "BACKDOOR_PORT":
                incident.setName("Backdoor Port Opened (TCP 4444)");
                incident.setType("C2 Beacon");
                incident.setAiExplanation("A rogue PowerShell process opened a listening port on TCP 4444.");
                immediateAction = "Identify Listening Port";
                recoveryWorkflow = "CLOSE_BACKDOOR";
                break;
            case "LATERAL_MOVEMENT":
                incident.setName("Lateral Movement (Guest Admin)");
                incident.setType("Identity");
                incident.setAiExplanation("The built-in Guest account was enabled and added to the Administrators group.");
                immediateAction = "Identify Privilege Escalation";
                recoveryWorkflow = "REMOVE_GUEST_ADMIN";
                break;
            case "DATA_EXFILTRATION":
                incident.setName("Massive Data Exfiltration");
                incident.setType("Exfiltration");
                incident.setAiExplanation("Unusually high outbound network traffic detected simulating data theft.");
                immediateAction = "Isolate Network";
                recoveryWorkflow = "STOP_EXFILTRATION";
                break;
            case "SHOW_TEST_ENFORCEMENT":
                incident.setName("Safe Test Security Event");
                incident.setType("Verification");
                incident.setAiExplanation("Manual verification event for safe end-to-end overlay and pipeline testing.");
                immediateAction = "Display Safe Test HUD";
                recoveryWorkflow = "SHOW_TEST_ENFORCEMENT";
                break;
            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown attack type: " + attackType));
        }

        incident = incidentRepository.saveAndFlush(incident);
        recoveryService.generateRecoveryStepsForIncident(incident.getId(), immediateAction, recoveryWorkflow);

        // 1. Dispatch attack execution to agent immediately
        com.astra.backend.entity.DeviceCommand cmd = commandDispatchService.queueCommand(
                targetDevice.getId(),
                incident.getId(),
                "EXECUTE_SAFE_ATTACK",
                "{\"target\":\"" + attackType + "\"}"
        );
        
        // 2. Broadcast immediately to SOC WebSocket clients for simultaneous real-time execution
        notificationService.sendNotification("threats", incident);
        notificationService.sendNotification("timeline", Map.of(
            "event", "NEW_INCIDENT", 
            "incident", incident,
            "immediateAction", immediateAction,
            "animation", "pulse_red"
        ));

        return ResponseEntity.ok(Map.of(
                "status", "Attack Dispatched",
                "incidentId", incident.getId().toString(),
                "commandId", cmd != null && cmd.getId() != null ? cmd.getId().toString() : ""
        ));
    }
}
