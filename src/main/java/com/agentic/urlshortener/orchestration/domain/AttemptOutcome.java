package com.agentic.urlshortener.orchestration.domain;

/** Outcome of one stage attempt (data-model.md, "StageAttempt"). */
public enum AttemptOutcome {
    SUCCEEDED,
    FAILED_TRANSIENT,
    FAILED_PERMANENT,
    TIMED_OUT,
    INTERRUPTED,
    DISCARDED,
    NEEDS_CLARIFICATION,
    POLICY_BLOCKED,
    REUSED
}
