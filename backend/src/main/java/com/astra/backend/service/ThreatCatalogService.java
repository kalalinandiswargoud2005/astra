package com.astra.backend.service;

import com.astra.backend.entity.ThreatCatalog;
import com.astra.backend.repository.ThreatCatalogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatCatalogService {

    private final ThreatCatalogRepository repository;
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.astra.backend.simulation.ThreatLibraryService threatLibraryService;

    private final Map<String, ThreatCatalog> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reloadCache();
    }

    @Scheduled(fixedRateString = "${threat.catalog.reload.rate:300000}") // Reload every 5 minutes
    public void reloadCache() {
        log.info("Reloading Threat Catalog Cache from Database...");
        List<ThreatCatalog> threats = repository.findByIsActiveTrue();
        cache.clear();
        for (ThreatCatalog t : threats) {
            cache.put(t.getThreatId(), t);
        }
        log.info("Successfully loaded {} threats into memory cache.", cache.size());
    }

    public List<ThreatCatalog> getAllThreats() {
        return List.copyOf(cache.values());
    }

    public Optional<ThreatCatalog> getThreatById(String threatId) {
        if (threatId == null) return Optional.empty();
        ThreatCatalog existing = cache.get(threatId);
        if (existing != null) {
            return Optional.of(existing);
        }

        // Fallback: check ThreatLibraryService scenarios
        if (threatLibraryService != null) {
            com.astra.backend.dto.ScenarioDto scenario = threatLibraryService.getScenarioById(threatId);
            if (scenario != null) {
                ThreatCatalog dynamicCatalog = mapScenarioToCatalog(scenario);
                cache.put(threatId, dynamicCatalog);
                return Optional.of(dynamicCatalog);
            }
        }
        return Optional.empty();
    }

    public ThreatCatalog findByName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        ThreatCatalog found = cache.values().stream()
            .filter(t -> t.getThreatName() != null && t.getThreatName().toLowerCase().contains(lower))
            .findFirst()
            .orElse(cache.values().stream()
                .filter(t -> lower.contains(t.getThreatName() != null ? t.getThreatName().toLowerCase() : ""))
                .findFirst()
                .orElse(null));

        if (found != null) return found;

        // Fallback: search ThreatLibraryService scenarios
        if (threatLibraryService != null) {
            for (com.astra.backend.dto.ScenarioDto scenario : threatLibraryService.getAllScenarios()) {
                if (scenario.getThreatName() != null &&
                        (scenario.getThreatName().toLowerCase().contains(lower) || lower.contains(scenario.getThreatName().toLowerCase()))) {
                    ThreatCatalog dynamicCatalog = mapScenarioToCatalog(scenario);
                    cache.put(scenario.getThreatId(), dynamicCatalog);
                    return dynamicCatalog;
                }
            }
        }
        return null;
    }

    private ThreatCatalog mapScenarioToCatalog(com.astra.backend.dto.ScenarioDto s) {
        ThreatCatalog c = new ThreatCatalog();
        c.setId(UUID.randomUUID());
        c.setThreatId(s.getThreatId());
        c.setThreatName(s.getThreatName());
        c.setCategory(s.getCategory() != null ? s.getCategory() : "Endpoint Security");
        c.setSeverity(s.getSeverity() != null ? s.getSeverity() : "HIGH");
        c.setDescription(s.getDescription() != null ? s.getDescription() : s.getAiSummary());
        c.setDetectionMethod(s.getDetectionLogic());
        c.setImmediateAction(s.getImmediateAction() != null ? s.getImmediateAction() : "Isolate system and terminate rogue process");

        if (s.getRecoveryWorkflow() != null && !s.getRecoveryWorkflow().isBlank()) {
            String[] steps = s.getRecoveryWorkflow().split(",");
            if (steps.length > 0) c.setRecoveryStep1(steps[0].trim());
            if (steps.length > 1) c.setRecoveryStep2(steps[1].trim());
            if (steps.length > 2) c.setRecoveryStep3(steps[2].trim());
            if (steps.length > 3) c.setRecoveryStep4(steps[3].trim());
            if (steps.length > 4) c.setRecoveryStep5(steps[4].trim());
        }
        return c;
    }
}

