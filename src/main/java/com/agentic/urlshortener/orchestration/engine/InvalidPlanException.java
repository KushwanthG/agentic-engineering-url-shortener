package com.agentic.urlshortener.orchestration.engine;

import java.util.List;

/** A plan that violates a structural invariant; it must never execute (FR-ORC-02). */
public class InvalidPlanException extends RuntimeException {

    private final List<String> violations;

    public InvalidPlanException(List<String> violations) {
        super("Invalid plan: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
