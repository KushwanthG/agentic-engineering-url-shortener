package com.agentic.urlshortener.orchestration.domain;

/** Failure classification (plan.md §6): only transient failures are retried. */
public enum FailureClass {
    TRANSIENT,
    PERMANENT
}
