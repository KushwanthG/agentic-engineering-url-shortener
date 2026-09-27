package com.agentic.urlshortener.orchestration.domain;

/** Workflow run status (data-model.md, "Run status"). */
public enum RunStatus {
    CREATED,
    RUNNING,
    AWAITING_HUMAN,
    PAUSED,
    COMPENSATING,
    COMPLETED,
    REJECTED,
    SAFE_STOPPED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == SAFE_STOPPED;
    }
}
