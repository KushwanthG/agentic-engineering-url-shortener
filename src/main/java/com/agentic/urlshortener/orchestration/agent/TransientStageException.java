package com.agentic.urlshortener.orchestration.agent;

/** An agent failure that may succeed when retried (for example a busy dependency); retried with backoff (FR-REL-02). */
public class TransientStageException extends RuntimeException {

    public TransientStageException(String message) {
        super(message);
    }

    public TransientStageException(String message, Throwable cause) {
        super(message, cause);
    }
}
