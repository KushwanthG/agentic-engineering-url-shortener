package com.agentic.urlshortener.orchestration.engine;

/** A transition the state model prohibits; it is rejected and audited as {@code ILLEGAL_TRANSITION} (FR-ORC-11). */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String subject, Enum<?> from, Enum<?> to) {
        super("Illegal " + subject + " transition " + from.name() + " -> " + to.name());
    }

    public IllegalTransitionException(String message) {
        super(message);
    }
}
