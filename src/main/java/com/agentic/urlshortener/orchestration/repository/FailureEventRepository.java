package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.StageType;

public interface FailureEventRepository extends JpaRepository<FailureEvent, UUID> {

    List<FailureEvent> findByRunId(UUID runId);

    Optional<FailureEvent> findFirstByRunIdAndStageKeyAndGenerationAndStatus(UUID runId, StageType stageKey, int generation,
            String status);
}
