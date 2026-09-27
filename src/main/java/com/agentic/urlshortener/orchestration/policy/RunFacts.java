package com.agentic.urlshortener.orchestration.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;

/** Read-only facts about a run that policy evaluation and reporting need beyond the artifacts. */
public interface RunFacts {

    /** A decision as reported in summaries. */
    record DecisionSummary(String stageKey, String type, String outcome, String actorId, String actorRole, String rationale,
            Instant createdAt) {
    }

    /** A context builder pre-filled with the run's facts; the caller adds the current artifacts. */
    PolicyContext.Builder forRun(UUID runId);

    /** The run's policy exceptions (approved or not); the engine applies only effective ones. */
    List<PolicyExceptionRecord> exceptions(UUID runId);

    /** The run's valid decisions in order; none by default. */
    default List<DecisionSummary> decisions(UUID runId) {
        return List.of();
    }
}
