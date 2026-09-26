package com.agentic.urlshortener.orchestration.domain;

/** Stage node status (data-model.md, "Stage status"). */
public enum StageStatus {
    PENDING,
    READY,
    RUNNING,
    RETRY_WAIT,
    AWAITING_DECISION,
    SUCCEEDED,
    SKIPPED,
    FAILED,
    COMPENSATED,
    CANCELLED,
    REMOVED;

    /** No transition leaves a terminal node state. */
    public boolean isTerminal() {
        return this == FAILED || this == COMPENSATED || this == CANCELLED || this == REMOVED;
    }

    /** Satisfies a dependency: dependents may start. */
    public boolean satisfiesDependency() {
        return this == SUCCEEDED || this == SKIPPED;
    }
}
