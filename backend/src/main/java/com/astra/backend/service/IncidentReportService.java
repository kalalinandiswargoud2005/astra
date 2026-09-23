package com.astra.backend.service;

import com.astra.backend.entity.Incident;
import com.astra.backend.entity.IncidentReport;
import com.astra.backend.entity.DeviceCommand;
import com.astra.backend.repository.IncidentReportRepository;
import com.astra.backend.repository.DeviceCommandRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentReportService {

    private final IncidentReportRepository reportRepository;
    private final DeviceCommandRepository commandRepository;
    private final ObjectMapper objectMapper;

    public void generateReport(Incident incident) {
        log.info("Generating incident report for incident {}", incident.getId());
        
        // Don't generate duplicate reports
        if (reportRepository.findByIncidentId(incident.getId()).isPresent()) {
            return;
        }

        List<DeviceCommand> commands = commandRepository.findByIncidentId(incident.getId());
        
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("incidentId", incident.getId().toString());
        reportData.put("name", incident.getName());
        reportData.put("severity", incident.getSeverity());
        reportData.put("targetDevice", incident.getTarget());
        reportData.put("status", incident.getStatus());
        reportData.put("created_at", incident.getCreatedAt() != null ? incident.getCreatedAt().toString() : "");
        reportData.put("resolved_at", incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : "");
        
        List<Map<String, String>> actions = commands.stream().map(cmd -> {
            Map<String, String> act = new HashMap<>();
            act.put("command", cmd.getCommandType());
            act.put("status", cmd.getStatus());
            act.put("result", cmd.getResult() != null ? cmd.getResult() : "");
            return act;
        }).toList();
        
        reportData.put("actions_taken", actions);

        try {
            IncidentReport report = IncidentReport.builder()
                .incidentId(incident.getId())
                .reportContent(objectMapper.writeValueAsString(reportData))
                .build();
            reportRepository.save(report);
            log.info("Report generated successfully");
        } catch (Exception e) {
            log.error("Failed to generate report JSON", e);
        }
    }
}

