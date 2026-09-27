package com.agentic.urlshortener.orchestration.agent;

import java.time.Duration;
import java.util.List;

import com.agentic.urlshortener.orchestration.domain.FailureClass;

/** Outcome of one agent execution (plan.md §3, "Agent contract"). */
public sealed interface StageResult {

    /** The stage produced its artifacts; the engine checks its exit criteria. */
    record Succeeded(List<ArtifactDraft> artifacts, String notes) implements StageResult {
        public Succeeded {
            artifacts = List.copyOf(artifacts);
        }
    }

    /** The stage failed; only transient failures are retried. */
    record Failed(FailureClass failureClass, String reason) implements StageResult {
    }

    /**
     * Produced by the dispatcher, never by an agent: the attempt exceeded its timeout, was interrupted,
     * and counts as a transient failure; a result the agent returns later is discarded.
     */
    record TimedOut(Duration timeout) implements StageResult {
    }

    /** The stage found a blocking ambiguity; the engine opens a clarification for its dependents. */
    record NeedsClarification(ArtifactDraft clarificationRequest, String reason) implements StageResult {
    }

    /** A mandatory policy failed; the stage waits for a policy-exception decision. */
    record PolicyBlocked(List<ArtifactDraft> artifacts, List<String> blockingPolicies, String reason) implements StageResult {
        public PolicyBlocked {
            artifacts = List.copyOf(artifacts);
            blockingPolicies = List.copyOf(blockingPolicies);
        }
    }
}
