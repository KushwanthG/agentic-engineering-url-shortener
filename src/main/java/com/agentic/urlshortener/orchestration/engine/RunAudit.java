package com.agentic.urlshortener.orchestration.engine;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;

/** Writes the engine's audit events (catalog in data-model.md) to the run's hash chain. */
@Component
public class RunAudit {

    public static final String SYSTEM = "orchestrator";

    private final AuditService audit;

    public RunAudit(AuditService audit) {
        this.audit = audit;
    }

    public void system(UUID runId, String action, String target, String result, String reason, Map<String, ?> details) {
        record(runId, ActorType.SYSTEM, SYSTEM, action, target, null, null, result, reason, details);
    }

    public void transition(UUID runId, String target, Enum<?> from, Enum<?> to, String reason) {
        record(runId, ActorType.SYSTEM, SYSTEM, "STAGE_TRANSITION", target, from.name(), to.name(), "OK", reason, null);
    }

    public void runTransition(UUID runId, Enum<?> from, Enum<?> to, String reason) {
        record(runId, ActorType.SYSTEM, SYSTEM, "RUN_TRANSITION", "RUN", from.name(), to.name(), "OK", reason, null);
    }

    public void record(UUID runId, ActorType actorType, String actorId, String action, String target, String fromState,
            String toState, String result, String reason, Map<String, ?> details) {
        audit.append(new AuditRecord(runId, actorType, actorId, action, target, fromState, toState, result, reason,
                details == null || details.isEmpty() ? null : CanonicalJson.write(details)));
    }
}
