package com.astra.backend.repository;

import com.astra.backend.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    long countByStatus(String status);
    List<Incident> findByStatusOrderByCreatedAtDesc(String status);
    List<Incident> findByStatusNotOrderByCreatedAtDesc(String status);
}

