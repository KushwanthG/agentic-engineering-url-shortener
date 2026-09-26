package com.agentic.urlshortener.orchestration.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.reliability.SafeStopService;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Operator controls (FR-REL-07, FR-REL-06): pause (no new stage starts; in-flight attempts finish),
 * resume, and safe-stop. Each action is recorded as an {@code OPERATOR_ACTION} decision by the
 * authenticated release owner and audited.
 */
@Service
public class OperationsService {

    private static final Set<RunStatus> PAUSABLE = Set.of(RunStatus.RUNNING, RunStatus.AWAITING_HUMAN);

    private final WorkflowRunRepository runs;
    private final DecisionRepository decisions;
    private final RunCoordinator coordinator;
    private final SafeStopService safeStops;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OperationsService(WorkflowRunRepository runs, DecisionRepository decisions, RunCoordinator coordinator,
            SafeStopService safeStops, RunLocks locks, RunAudit audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.decisions = decisions;
        this.coordinator = coordinator;
        this.safeStops = safeStops;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void pause(UUID runId, String reason, ApiPrincipal operator) {
        change(runId, operator, reason, "PAUSED", run -> {
            if (!PAUSABLE.contains(run.getStatus())) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE, "Only a running or waiting run can be paused (status " + run.getStatus() + ").");
            }
            return RunStatus.PAUSED;
        });
    }

    public void resume(UUID runId, String reason, ApiPrincipal operator) {
        change(runId, operator, reason, "RESUMED", run -> {
            if (run.getStatus() != RunStatus.PAUSED) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE, "Only a paused run can be resumed (status " + run.getStatus() + ").");
            }
            return RunStatus.RUNNING;
        });
        coordinator.advance(runId);
    }

    public void safeStop(UUID runId, String reason, ApiPrincipal operator) {
        locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = load(runId);
            record(run, operator, reason, "SAFE_STOP_REQUESTED", clockNow());
            return null;
        }));
        String terminalReason = "operator safe-stop by " + operator.id() + ": " + reason;
        if (!safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, terminalReason, ActorType.HUMAN, operator.id())) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run ended before the safe-stop was applied.");
        }
    }

    private void change(UUID runId, ApiPrincipal operator, String reason, String outcome,
            Function<WorkflowRun, RunStatus> target) {
        locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = load(runId);
            RunStatus to = target.apply(run);
            RunStatus from = run.getStatus();
            Instant now = clockNow();
            run.transitionTo(to, now);
            audit.runTransition(runId, from, to, outcome.toLowerCase() + " by " + operator.id() + ": " + reason);
            record(run, operator, reason, outcome, now);
            return null;
        }));
    }

    private WorkflowRun load(UUID runId) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + "; operator actions are no longer possible.");
        }
        return run;
    }

    private void record(WorkflowRun run, ApiPrincipal operator, String reason, String outcome, Instant now) {
        decisions.save(Decision.create(run.getId(), null, DecisionType.OPERATOR_ACTION, outcome, ActorType.HUMAN, operator.id(),
                Role.RELEASE_OWNER.name(), reason, null, null, now));
        audit.record(run.getId(), ActorType.HUMAN, operator.id(), "OPERATOR_ACTION", "RUN", null, outcome, "OK", reason, null);
    }

    private Instant clockNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
