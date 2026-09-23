package com.astra.backend.simulation;

import com.astra.backend.dto.ScenarioDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatLibraryService {

    private final ObjectMapper objectMapper;
    private List<ScenarioDto> scenarios = new ArrayList<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("scenarios.json").getInputStream()) {
            scenarios = objectMapper.readValue(is, new TypeReference<List<ScenarioDto>>() {});
            log.info("Successfully loaded {} enterprise scenarios into Threat Library.", scenarios.size());
        } catch (Exception e) {
            log.error("Failed to load scenarios.json into Threat Library", e);
        }
    }

    public List<ScenarioDto> getAllScenarios() {
        return Collections.unmodifiableList(scenarios);
    }

    public ScenarioDto getScenarioById(String threatId) {
        return scenarios.stream()
                .filter(s -> s.getThreatId().equals(threatId))
                .findFirst()
                .orElse(null);
    }
}

