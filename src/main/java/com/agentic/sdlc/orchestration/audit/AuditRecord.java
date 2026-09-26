package com.agentic.sdlc.orchestration.audit;

import java.util.UUID;

import com.agentic.sdlc.orchestration.model.ActorType;

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

    public String chainId() {
        return runId == null ? AuditService.GLOBAL_CHAIN : runId.toString();
    }
}
