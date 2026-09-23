package com.astra.backend.service;

import com.astra.backend.entity.Incident;
import com.astra.backend.repository.DeviceCommandRepository;
import com.astra.backend.repository.IncidentReportRepository;
import com.astra.backend.repository.IncidentRepository;
import com.astra.backend.repository.RecoveryStepRepository;
import com.astra.backend.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatService {

    private final IncidentRepository incidentRepository;
    private final RecoveryStepRepository recoveryStepRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final WebSocketPublisher webSocketPublisher;

    public List<Incident> getActiveThreats() {
        return incidentRepository.findByStatusNotOrderByCreatedAtDesc("RESOLVED");
    }
    
    public List<Incident> getResolvedThreats() {
        return incidentRepository.findByStatusOrderByCreatedAtDesc("RESOLVED");
    }
    
    public void resolveThreat(UUID id) {
        incidentRepository.findById(id).ifPresent(incident -> {
            incident.setStatus("RESOLVED");
            incident.setResolvedAt(LocalDateTime.now());
            Incident saved = incidentRepository.save(incident);
            webSocketPublisher.broadcastNewThreat(saved);
        });
    }

    @Transactional
    public void clearAllThreats() {
        log.info("[THREAT-SERVICE] Purging all incidents, recovery steps, incident reports, and commands queue via fast batch...");
        try {
            try {
                recoveryStepRepository.deleteAllInBatch();
            } catch (Exception e) {
                log.warn("Could not delete recovery steps in batch: {}", e.getMessage());
            }
            try {
                incidentReportRepository.deleteAllInBatch();
            } catch (Exception e) {
                log.warn("Could not delete incident reports in batch: {}", e.getMessage());
            }
            try {
                deviceCommandRepository.deleteAllInBatch();
            } catch (Exception e) {
                log.warn("Could not delete device commands in batch: {}", e.getMessage());
            }
            try {
                incidentRepository.deleteAllInBatch();
            } catch (Exception e) {
                log.warn("Could not delete incidents in batch: {}", e.getMessage());
            }
            
            // Broadcast clear notification
            webSocketPublisher.broadcastClearThreats();
            log.info("[THREAT-SERVICE] All threat records purged in batch successfully.");
        } catch (Exception e) {
            log.error("[THREAT-SERVICE] Error clearing threats: {}", e.getMessage(), e);
        }
    }
}

