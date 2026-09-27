package com.agentic.urlshortener.orchestration.reliability;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;

/**
 * Entry point for safe-stop triggers outside the scheduler (FR-REL-06): operator requests, rejected
 * policy exceptions, exceeded clarification rounds. Stage failures and gate deadlines are detected by
 * the coordinator itself and run the same procedure ({@code RunCoordinator#safeStop}).
 */
@Service
public class SafeStopService {

    /** Why a run was safe-stopped; recorded in the SAFE_STOP decision and the RUN_TERMINATED event. */
    public enum Trigger {
        STAGE_FAILED,
        GATE_DEADLINE,
        OPERATOR_REQUEST,
        POLICY_EXCEPTION_REJECTED,
        CLARIFICATION_ROUNDS_EXCEEDED,
        AUTONOMY_BUDGET_EXCEEDED
    }

    private final RunCoordinator coordinator;

    public SafeStopService(RunCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /** Safe-stops the run; returns false when it was already terminal (one terminal outcome per run). */
    public boolean stop(UUID runId, Trigger trigger, String reason, ActorType actorType, String actorId) {
        return coordinator.safeStop(runId, trigger.name(), reason, actorType, actorId);
    }
}
