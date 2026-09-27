package com.agentic.urlshortener.orchestration.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;

public interface StageNodeRepository extends JpaRepository<StageNode, UUID> {

    Optional<StageNode> findByRunIdAndStageKey(UUID runId, StageType stageKey);

    List<StageNode> findByRunIdOrderByStageKeyAsc(UUID runId);

    long countByStatus(StageStatus status);

    List<StageNode> findByStatusAndDecisionDeadlineBefore(StageStatus status, Instant deadline);
}
