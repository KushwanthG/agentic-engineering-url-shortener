package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageType;

public interface StageAttemptRepository extends JpaRepository<StageAttempt, UUID> {

    List<StageAttempt> findByRunIdOrderByStartedAtAsc(UUID runId);

    Optional<StageAttempt> findByRunIdAndStageKeyAndGenerationAndAttemptNo(UUID runId, StageType stageKey, int generation, int attemptNo);

    List<StageAttempt> findByRunIdAndFinishedAtIsNull(UUID runId);

    /** Highest scheduling cycle used by the run so far (0 when none). */
    @Query("select coalesce(max(a.schedulingCycle), 0) from StageAttempt a where a.runId = :runId")
    long maxSchedulingCycle(@Param("runId") UUID runId);
}
