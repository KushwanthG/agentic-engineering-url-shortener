package com.agentic.urlshortener.orchestration.domain;

/** Kinds of recorded decisions (data-model.md, "Decision"). */
public enum DecisionType {
    GATE,
    CLARIFICATION_ANSWER,
    CHANGE_REQUEST,
    CHANGE_DECISION,
    EXCEPTION_REQUEST,
    EXCEPTION_DECISION,
    FALLBACK_USED,
    REPLAN,
    OPERATOR_ACTION,
    SAFE_STOP
}
