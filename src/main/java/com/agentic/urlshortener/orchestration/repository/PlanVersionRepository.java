package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.PlanVersion;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {

    List<PlanVersion> findByRunIdOrderByVersionAsc(UUID runId);
}
