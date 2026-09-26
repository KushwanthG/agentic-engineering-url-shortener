package com.agentic.urlshortener.orchestration.domain;

import java.util.UUID;

import com.agentic.urlshortener.orchestration.domain.ActorType;

/**
 * An audit fact to append. {@code runId == null} places the event on the {@code GLOBAL} chain.
 * {@code details} is optional JSON; it must never contain secrets (NFR-SEC-03).
 */
public record AuditRecord(
        UUID runId,
        ActorType actorType,
        String actorId,
        String action,
        String target,
        String fromState,
        String toState,
        String result,
        String reason,
        String details) {

    /** Chain id of events that belong to no workflow run. */
    public static final String GLOBAL_CHAIN = "GLOBAL";

    public String chainId() {
        return runId == null ? GLOBAL_CHAIN : runId.toString();
    }
}
