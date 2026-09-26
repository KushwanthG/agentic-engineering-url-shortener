package com.agentic.urlshortener.orchestration.agent;

/** An agent failure that retrying cannot fix (for example invalid input); never retried (FR-REL-02). */
public class PermanentStageException extends RuntimeException {

    public PermanentStageException(String message) {
        super(message);
    }
}
