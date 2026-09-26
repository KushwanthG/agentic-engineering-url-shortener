package com.agentic.urlshortener.orchestration.domain;

/** What a stage in {@code AWAITING_DECISION} is waiting for. */
public enum AwaitingType {
    APPROVAL,
    CLARIFICATION,
    POLICY_EXCEPTION,
    CHANGE_APPROVAL
}
