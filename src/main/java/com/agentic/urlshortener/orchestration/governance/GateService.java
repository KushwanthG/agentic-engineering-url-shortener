package com.agentic.urlshortener.orchestration.governance;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Human decisions on approval gates (FR-GOV-01, 03, 09; ADR-008). Only an authenticated principal
 * holding the gate's role may decide; the decision records the actor, role, rationale, and the
 * fingerprints of the reviewed artifacts. The engine can never mark a gate succeeded on its own:
 * this service is the only path from {@code AWAITING_DECISION} to {@code SUCCEEDED} for a gate.
 */
@Service
public class GateService {

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final DecisionRepository decisions;
    private final ArtifactStore artifacts;
    private final RunCoordinator coordinator;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GateService(WorkflowRunRepository runs, StageNodeRepository nodes, DecisionRepository decisions, ArtifactStore artifacts,
            RunCoordinator coordinator, RunLocks locks, RunAudit audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.nodes = nodes;
        this.decisions = decisions;
        this.artifacts = artifacts;
        this.coordinator = coordinator;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Decision decide(UUID runId, StageType gate, GateDecisionRequest request, ApiPrincipal principal) {
        Decision decision = locks.withLock(runId, () -> {
            ApiException refusal = tx.execute(status -> check(runId, gate, principal));
            if (refusal != null) {
                audit.record(runId, ActorType.HUMAN, principal.id(), "DECISION_REFUSED", gate.name(), null, null, "REFUSED",
                        refusal.getMessage(), null);
                throw refusal;
            }
            try {
                return tx.execute(status -> record(runId, gate, request, principal));
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new ApiException(ErrorCode.CONCURRENT_DECISION, "Another decision on " + gate + " was recorded first.");
            }
        });
        coordinator.advance(runId);
        return decision;
    }

    /** Returns the reason to refuse the decision, or null when it may be recorded. */
    private ApiException check(UUID runId, StageType gate, ApiPrincipal principal) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + "; no further decisions are possible.");
        }
        if (!ReviewBundles.isApprovalGate(gate)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not an approval gate decided on this endpoint.");
        }
        StageNode node = nodes.findByRunIdAndStageKey(runId, gate)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not part of this run's plan."));
        if (node.getStatus() != StageStatus.AWAITING_DECISION || node.getAwaiting() != AwaitingType.APPROVAL) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not awaiting a decision (status " + node.getStatus() + ").");
        }
        if (!principal.hasRole(gate.requiredRole())) {
            return new ApiException(ErrorCode.FORBIDDEN, "Deciding " + gate + " requires the role " + gate.requiredRole() + ".");
        }
        return null;
    }

    private Decision record(UUID runId, StageType gate, GateDecisionRequest request, ApiPrincipal principal) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        WorkflowRun run = runs.findById(runId).orElseThrow();
        StageNode node = nodes.findByRunIdAndStageKey(runId, gate).orElseThrow();
        String outcome = request.approve() ? "APPROVED" : "REJECTED";
        Decision decision = decisions.save(Decision.create(runId, gate, DecisionType.GATE, outcome, ActorType.HUMAN, principal.id(),
                gate.requiredRole().name(), request.rationale(), null, CanonicalJson.write(reviewBundle(runId, gate)), now));
        audit.record(runId, ActorType.HUMAN, principal.id(), "GATE_DECIDED", gate.name(), null, outcome, outcome,
                request.rationale(), Map.of("decisionId", decision.getId().toString(), "role", gate.requiredRole().name()));
        if (request.approve()) {
            node.succeedGate(decision, now);
            audit.transition(runId, gate.name(), StageStatus.AWAITING_DECISION, StageStatus.SUCCEEDED,
                    "approved by " + principal.id());
        } else {
            String reason = gate + " rejected by " + principal.id() + ": " + request.rationale();
            node.fail(FailureClass.PERMANENT, reason, now);
            audit.transition(runId, gate.name(), StageStatus.AWAITING_DECISION, StageStatus.FAILED, reason);
            reject(run, reason, now);
        }
        nodes.saveAndFlush(node);
        return decision;
    }

    private void reject(WorkflowRun run, String reason, Instant now) {
        coordinator.markStopping(run.getId());
        RunStatus from = run.getStatus();
        if (from == RunStatus.RUNNING || from == RunStatus.PAUSED) {
            run.transitionTo(RunStatus.COMPENSATING, now);
            audit.runTransition(run.getId(), from, RunStatus.COMPENSATING, reason);
            from = RunStatus.COMPENSATING;
        }
        run.terminate(RunStatus.REJECTED, reason, now);
        audit.runTransition(run.getId(), from, RunStatus.REJECTED, reason);
        audit.system(run.getId(), "RUN_TERMINATED", "RUN", "REJECTED", reason, null);
    }

    private Map<String, String> reviewBundle(UUID runId, StageType gate) {
        Map<String, Artifact> current = artifacts.current(runId);
        Map<String, String> bound = new LinkedHashMap<>();
        for (String type : ReviewBundles.of(gate, AwaitingType.APPROVAL)) {
            Artifact artifact = current.get(type);
            if (artifact != null) {
                bound.put(type, artifact.getFingerprint());
            }
        }
        return bound;
    }
}
