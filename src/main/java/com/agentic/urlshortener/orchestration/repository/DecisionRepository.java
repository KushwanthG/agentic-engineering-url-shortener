package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.StageType;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByRunIdOrderByCreatedAtAsc(UUID runId);

    List<Decision> findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(UUID runId, StageType stageKey);
}
