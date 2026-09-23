package com.astra.backend.simulation;

import com.astra.backend.dto.ScenarioDto;
import com.astra.backend.entity.Device;
import com.astra.backend.entity.Incident;
import com.astra.backend.notification.NotificationService;
import com.astra.backend.repository.IncidentRepository;
import com.astra.backend.service.CommandDispatchService;
import com.astra.backend.service.DeviceService;
import com.astra.backend.service.RecoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final ThreatLibraryService threatLibraryService;
    private final NotificationService notificationService;
    private final IncidentRepository incidentRepository;
    private final RecoveryService recoveryService;
    private final CommandDispatchService commandDispatchService;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    /**
     * Trigger a random threat scenario.
     *
     * If target is provided, the threat will be triggered
     * specifically against that device.
     *
     * If target is not provided, an online device will be selected.
     */
    public Incident triggerRandomScenario(String target) {

        List<ScenarioDto> scenarios =
                threatLibraryService.getAllScenarios();

        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalStateException(
                    "No threat scenarios are available."
            );
        }

        ScenarioDto scenario =
                scenarios.get(
                        ThreadLocalRandom.current()
                                .nextInt(scenarios.size())
                );

        log.info(
                "Random simulation selected threat={}",
                scenario.getThreatName()
        );

        return triggerScenario(scenario, target);
    }

    /**
     * Return all available threat scenarios.
     */
    public List<ScenarioDto> getAllScenarios() {

        List<ScenarioDto> scenarios =
                threatLibraryService.getAllScenarios();

        return scenarios != null ? scenarios : List.of();
    }

    /**
     * Trigger a specific threat scenario by ID.
     */
    public Incident triggerScenarioById(
            String threatId,
            String target
    ) {

        if (threatId == null || threatId.isBlank()) {
            throw new IllegalArgumentException(
                    "Threat ID cannot be empty."
            );
        }

        ScenarioDto scenario =
                threatLibraryService.getScenarioById(threatId);

        if (scenario == null) {
            throw new IllegalArgumentException(
                    "Threat scenario not found: " + threatId
            );
        }

        return triggerScenario(scenario, target);
    }

    /**
     * Main simulation workflow.
     *
     * Flow:
     *
     * Threat Scenario
     *       ↓
     * Resolve Device
     *       ↓
     * Create Incident
     *       ↓
     * Save Incident
     *       ↓
     * Generate Recovery
     *       ↓
     * Dispatch Endpoint Alert
     *       ↓
     * Notify SOC Dashboard
     */
    public Incident triggerScenario(
            ScenarioDto scenario,
            String target
    ) {

        if (scenario == null) {
            throw new IllegalArgumentException(
                    "Threat scenario cannot be null."
            );
        }

        if (scenario.getThreatName() == null ||
                scenario.getThreatName().isBlank()) {

            throw new IllegalArgumentException(
                    "Threat scenario must have a threat name."
            );
        }

        log.info(
                "Starting threat simulation. threat={} target={}",
                scenario.getThreatName(),
                target
        );

        /*
         * ---------------------------------------------------------
         * 1. RESOLVE TARGET DEVICE
         * ---------------------------------------------------------
         */

        List<Device> devices = deviceService.getAllDevices();

        if (devices == null) {
            devices = List.of();
        }

        boolean explicitTarget =
                target != null && !target.isBlank();

        Device targetDevice =
                resolveTargetDevice(target, devices);

        /*
         * If user explicitly selected a device,
         * do NOT silently attack another device.
         */
        if (explicitTarget && targetDevice == null) {

            throw new IllegalArgumentException(
                    "Target device not found: " + target
            );
        }

        /*
         * If no target was provided, choose an online device.
         */
        if (!explicitTarget && targetDevice == null) {

            targetDevice =
                    findOnlineDevice(devices);
        }

        /*
         * ---------------------------------------------------------
         * 2. DETERMINE TARGET NAME
         * ---------------------------------------------------------
         */

        String targetName;

        if (targetDevice != null) {

            targetName = targetDevice.getName();

            if (targetName == null || targetName.isBlank()) {
                targetName = targetDevice.getId().toString();
            }

        } else {

            /*
             * No real device available.
             *
             * This is useful for pure dashboard simulation.
             */
            targetName = generateSimulationTarget();
        }

        log.info(
                "Simulation target resolved. device={} target={}",
                targetDevice != null
                        ? targetDevice.getId()
                        : "SIMULATED",
                targetName
        );

        /*
         * ---------------------------------------------------------
         * 3. CREATE INCIDENT
         * ---------------------------------------------------------
         */

        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .name(scenario.getThreatName())
                .type(scenario.getCategory())
                .severity(scenario.getSeverity())
                .status("ACTIVE")
                .target(targetName)
                .aiExplanation(scenario.getAiSummary())
                .build();

        /*
         * ---------------------------------------------------------
         * 4. SAVE INCIDENT
         * ---------------------------------------------------------
         */

        incident = incidentRepository.save(incident);

        log.info(
                "Simulation incident created. incidentId={} threat={} target={}",
                incident.getId(),
                incident.getName(),
                incident.getTarget()
        );

        /*
         * ---------------------------------------------------------
         * 5. GENERATE RECOVERY STEPS
         * ---------------------------------------------------------
         */

        generateRecoverySteps(
                incident,
                scenario
        );

        /*
         * ---------------------------------------------------------
         * 6. SEND ALERT TO ENDPOINT
         * ---------------------------------------------------------
         */

        dispatchThreatAlert(
                targetDevice,
                incident,
                scenario
        );

        /*
         * ---------------------------------------------------------
         * 7. NOTIFY SOC DASHBOARD
         * ---------------------------------------------------------
         */

        notifySocDashboard(
                incident,
                scenario
        );

        log.info(
                "Threat simulation completed successfully. incidentId={}",
                incident.getId()
        );

        return incident;
    }

    /**
     * Find device by:
     *
     * 1. Device name
     * 2. Device ID
     */
    private Device resolveTargetDevice(
            String target,
            List<Device> devices
    ) {

        if (target == null ||
                target.isBlank()) {

            return null;
        }

        String requestedTarget =
                target.trim();

        for (Device device : devices) {

            if (device == null) {
                continue;
            }

            /*
             * Match device name.
             */
            if (device.getName() != null &&
                    device.getName()
                            .equalsIgnoreCase(requestedTarget)) {

                return device;
            }

            /*
             * Match device ID.
             */
            if (device.getId() != null &&
                    device.getId()
                            .toString()
                            .equalsIgnoreCase(requestedTarget)) {

                return device;
            }
        }

        return null;
    }

    /**
     * Find the first online device.
     */
    private Device findOnlineDevice(
            List<Device> devices
    ) {

        return devices.stream()
                .filter(device -> device != null)
                .filter(device ->
                        device.getStatus() != null &&
                        "ONLINE".equalsIgnoreCase(
                                device.getStatus()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Generate a fake target when there is no real endpoint.
     */
    private String generateSimulationTarget() {

        int lastOctet =
                ThreadLocalRandom.current()
                        .nextInt(1, 255);

        return "SIMULATED-DEVICE-01 / 10.0.0."
                + lastOctet;
    }

    /**
     * Generate recovery workflow for the incident.
     */
    private void generateRecoverySteps(
            Incident incident,
            ScenarioDto scenario
    ) {

        try {

            if (scenario.getDynamicRecovery() != null &&
                    !scenario.getDynamicRecovery().isEmpty()) {

                recoveryService
                        .generateDynamicRecoveryStepsForIncident(
                                incident.getId(),
                                scenario.getImmediateAction(),
                                scenario.getDynamicRecovery()
                        );

                log.info(
                        "Dynamic recovery generated. incidentId={}",
                        incident.getId()
                );

            } else {

                recoveryService
                        .generateRecoveryStepsForIncident(
                                incident.getId(),
                                scenario.getImmediateAction(),
                                scenario.getRecoveryWorkflow()
                        );

                log.info(
                        "Standard recovery generated. incidentId={}",
                        incident.getId()
                );
            }

        } catch (Exception e) {

            /*
             * Incident already exists in DB.
             *
             * We log the failure instead of hiding it.
             */
            log.error(
                    "Recovery generation failed. incidentId={}",
                    incident.getId(),
                    e
            );

            /*
             * Do not delete the incident.
             *
             * The SOC should still know that
             * a threat occurred.
             */
        }
    }

    /**
     * Send SHOW_THREAT_ALERT command to the endpoint.
     */
    private void dispatchThreatAlert(
            Device targetDevice,
            Incident incident,
            ScenarioDto scenario
    ) {

        /*
         * No real device.
         */
        if (targetDevice == null) {

            log.info(
                    "No physical endpoint available. " +
                    "Skipping endpoint command. incidentId={}",
                    incident.getId()
            );

            return;
        }

        /*
         * Device exists but is offline.
         */
        if (!"ONLINE".equalsIgnoreCase(
                targetDevice.getStatus()
        )) {

            log.info(
                    "Target device is offline. " +
                    "Skipping endpoint command. device={} incidentId={}",
                    targetDevice.getName(),
                    incident.getId()
            );

            return;
        }

        try {

            /*
             * Proper JSON generation using Jackson.
             *
             * This is safer than manually creating:
             * {"target":"..."}
             */
            String payload =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "target",
                                    scenario.getThreatName(),
                                    "incidentId",
                                    incident.getId().toString(),
                                    "severity",
                                    scenario.getSeverity()
                            )
                    );

            commandDispatchService.queueCommand(
                    targetDevice.getId(),
                    incident.getId(),
                    "SHOW_THREAT_ALERT",
                    payload
            );

            log.info(
                    "Threat alert queued. device={} incidentId={}",
                    targetDevice.getName(),
                    incident.getId()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to dispatch threat alert. " +
                    "device={} incidentId={}",
                    targetDevice.getName(),
                    incident.getId(),
                    e
            );
        }
    }

    /**
     * Notify SOC dashboard through WebSocket/notification service.
     */
    private void notifySocDashboard(
            Incident incident,
            ScenarioDto scenario
    ) {

        /*
         * THREATS CHANNEL
         */
        try {

            notificationService.sendNotification(
                    "threats",
                    incident
            );

            log.debug(
                    "Threat notification sent. incidentId={}",
                    incident.getId()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to send threat notification. incidentId={}",
                    incident.getId(),
                    e
            );
        }

        /*
         * TIMELINE CHANNEL
         */
        try {

            Map<String, Object> timelineEvent =
                    Map.of(
                            "event",
                            "NEW_INCIDENT",

                            "incident",
                            incident,

                            "immediateAction",
                            scenario.getImmediateAction() != null
                                    ? scenario.getImmediateAction()
                                    : "Immediate Response Triggered",

                            "animation",
                            scenario.getDashboardAnimation() != null
                                    ? scenario.getDashboardAnimation()
                                    : ""
                    );

            notificationService.sendNotification(
                    "timeline",
                    timelineEvent
            );

            log.debug(
                    "Timeline notification sent. incidentId={}",
                    incident.getId()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to send timeline notification. incidentId={}",
                    incident.getId(),
                    e
            );
        }
    }
}
